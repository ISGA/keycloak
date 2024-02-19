package org.keycloak.models.cache.infinispan;

import org.infinispan.Cache;
import org.jboss.logging.Logger;
import org.keycloak.cluster.ClusterProvider;
import org.keycloak.models.InvalidationManager;
import org.keycloak.models.cache.infinispan.events.InvalidationEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.cache.infinispan.entities.Revisioned;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 *
 * Some notes on how this works:

 * This implementation manages optimistic locking and version checks itself.  The reason is Infinispan just does behave
 * the way we need it to.  Not saying Infinispan is bad, just that we have specific caching requirements!
 *
 * This is an invalidation cache implementation and requires to caches:
 * Cache 1 is an Invalidation Cache
 * Cache 2 is a local-only revision number cache.
 *
 *
 * Each node in the cluster maintains its own revision number cache for each entry in the main invalidation cache.  This revision
 * cache holds the version counter for each cached entity.
 *
 * Cache listeners do not receive a @CacheEntryInvalidated event if that node does not have an entry for that item.  So, consider the following.

 1. Node 1 gets current counter for user.  There currently isn't one as this user isn't cached.
 2. Node 1 reads user from DB
 3. Node 2 updates user
 4. Node 2 calls cache.remove(user).  This does not result in an invalidation listener event to node 1!
 5. node 1 checks version counter, checks pass. Stale entry is cached.

 The issue is that Node 1 doesn't have an entry for the user, so it never receives an invalidation listener event from Node 2 thus it can't bump the version.  So, when node 1 goes to cache the user it is stale as the version number was never bumped.

 So how is this issue fixed?  here is pseudo code:

 1. Node 1 calls cacheManager.getCurrentRevision() to get the current local version counter of that User
 2. Node 1 getCurrentRevision() pulls current counter for that user
 3. Node 1 getCurrentRevision() adds a "invalidation.key.userid" to invalidation cache.  Its just a marker. nothing else
 4. Node 2 update user
 5. Node 2 does a cache.remove(user) cache.remove(invalidation.key.userid)
 6. Node 1 receives invalidation event for invalidation.key.userid. Bumps the version counter for that user
 7. node 1 version check fails, it doesn't cache the user
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public abstract class CacheManager {

    protected final Cache<String, Long> revisions;
    protected final Cache<String, Revisioned> cache;
    protected final UpdateCounter counter = new UpdateCounter();

    public CacheManager(Cache<String, Revisioned> cache, Cache<String, Long> revisions) {
        this.cache = cache;
        this.revisions = revisions;
    }

    protected abstract Logger getLogger();

    public Cache<String, Revisioned> getCache() {
        return cache;
    }

    public long getCurrentCounter() {
        return counter.current();
    }

    public Long getCurrentRevision(String id) {
        Long revision = revisions.get(id);
        if (revision == null) {
            revision = counter.current();
        }

        return revision;
    }

    public void endRevisionBatch() {
        try {
            revisions.endBatch(true);
        } catch (Exception e) {
        }

    }

    public <T extends Revisioned> T get(String id, Class<T> type) {
        Revisioned o = (Revisioned)cache.get(id);
        if (o == null) {
            return null;
        }
        Long rev = revisions.get(id);
        if (rev == null) {
            if (getLogger().isTraceEnabled()) {
                getLogger().tracev("get() missing rev {0}", id);
            }
            /* id is no longer in this.revisions
             ** => remove it also from this.cache
             ** to come back to a consistent state
             ** this allows caching the current version again
             */
            cache.remove(id);
            return null;
        }
        long oRev = o.getRevision() == null ? -1L : o.getRevision().longValue();
        if (rev > oRev) {
            if (getLogger().isTraceEnabled()) {
                getLogger().tracev("get() rev: {0} o.rev: {1}", rev.longValue(), oRev);
            }
            // the object in this.cache is outdated => remove it
            cache.remove(id);
            return null;
        }
        return o != null && type.isInstance(o) ? type.cast(o) : null;
    }

    public Object invalidateObject(String id) {
        Revisioned removed = (Revisioned)cache.remove(id);

        if (getLogger().isTraceEnabled()) {
            getLogger().tracef("Removed key='%s', value='%s' from cache", id, removed);
        }

        bumpVersion(id);
        return removed;
    }

    /**
     * This method is intended to allow for the invalidation of entire sets of keys within the cache that match
     * the given prefix. Rather than iterating through each object in the cache and dropping based on ID lookups,
     * find all the keys that match a composable prefix and drop everything after it.
     * @param prefix The prefix that should be used for matching
     * @return A set of objects representing all removed items from the cache
     */
    public Set<Object> invalidatePrefix(String prefix) {
        Set<String> keys = cache.keySet().stream().filter(key -> key.startsWith(prefix)).collect(Collectors.toSet());
        Set<Object> removedObjects = new HashSet<>();
        for(String key : keys) {
            Revisioned removed = cache.remove(key);
            removedObjects.add(removed);
            if (getLogger().isTraceEnabled()) {
                getLogger().tracef("Removed key='%s', value='%s' from cache", key, removed);
            }
            bumpVersion(key);
        }
        return removedObjects;
    }

    /**
     * Executes the stored invalidations in the {@link InvalidationManager} for this session
     */
    public void runInvalidations(InvalidationManager invalidationManager) {
        if(invalidationManager != null) {
            for (String model : invalidationManager.getModelInvalidations()) {
                invalidateObject(model);
            }
            for (String prefix : invalidationManager.getPrefixInvalidations()) {
                invalidatePrefix(prefix);
            }
        }
    }

    public boolean isPrefixInvalidated(String prefix, InvalidationManager invalidationManager) {
        if (invalidationManager != null) {
            return invalidationManager.isPrefixInvalidated(prefix);
        }
        return false;
    }

    public boolean isModelInvalidated(String modelId, InvalidationManager invalidationManager) {
        if (invalidationManager != null) {
            return invalidationManager.isModelInvalidated(modelId);
        }
        return false;
    }

    protected void bumpVersion(String id) {
        long next = counter.next();
        Object rev = revisions.put(id, next);
    }

    public void addRevisioned(Revisioned object, long startupRevision) {
        addRevisioned(object, startupRevision, -1);
    }

    public void addRevisioned(Revisioned object, long startupRevision, long lifespan) {
        //startRevisionBatch();
        String id = object.getId();
        try {
            //revisions.getAdvancedCache().lock(id);
            Long rev = revisions.get(id);
            if (rev == null) {
                rev = counter.current();
                revisions.put(id, rev);
            }
            revisions.startBatch();
            if (!revisions.getAdvancedCache().lock(id)) {
                if (getLogger().isTraceEnabled()) {
                    getLogger().tracev("Could not obtain version lock: {0}", id);
                }
                return;
            }
            rev = revisions.get(id);
            if (rev == null) {
                return;
            }
            if (rev > startupRevision) { // revision is ahead transaction start. Other transaction updated in the meantime. Don't cache
                if (getLogger().isTraceEnabled()) {
                    getLogger().tracev("Skipped cache. Current revision {0}, Transaction start revision {1}", object.getRevision(), startupRevision);
                }
                return;
            }
            if (rev.equals(object.getRevision())) {
                cache.putForExternalRead(id, object);
                return;
            }
            if (rev > object.getRevision()) { // revision is ahead, don't cache
                if (getLogger().isTraceEnabled()) getLogger().tracev("Skipped cache. Object revision {0}, Cache revision {1}", object.getRevision(), rev);
                return;
            }
            // revisions cache has a lower value than the object.revision, so update revision and add it to cache
            revisions.put(id, object.getRevision());
            if (lifespan < 0) cache.putForExternalRead(id, object);
            else cache.putForExternalRead(id, object, lifespan, TimeUnit.MILLISECONDS);
        } finally {
            endRevisionBatch();
        }

    }

    public void clear() {
        cache.clear();
        revisions.clear();
    }

    public void addInvalidations(Predicate<Map.Entry<String, Revisioned>> predicate, Set<String> invalidations) {
        Iterator<Map.Entry<String, Revisioned>> it = getEntryIterator(predicate);
        while (it.hasNext()) {
            invalidations.add(it.next().getKey());
        }
    }

    private Iterator<Map.Entry<String, Revisioned>> getEntryIterator(Predicate<Map.Entry<String, Revisioned>> predicate) {
        return cache
                .entrySet()
                .stream()
                .filter(predicate).iterator();
    }


    public void sendInvalidationEvents(KeycloakSession session, Collection<InvalidationEvent> invalidationEvents, String eventKey, InvalidationManager invalidationManager) {
        ClusterProvider clusterProvider = session.getProvider(ClusterProvider.class);

        // Maybe add InvalidationEvent, which will be collection of all invalidationEvents? That will reduce cluster traffic even more.
        for (InvalidationEvent event : invalidationEvents) {
            clusterProvider.notify(eventKey, event, true, ClusterProvider.DCNotify.ALL_DCS);
        }
        if (invalidationManager != null) {
            for (InvalidationEvent event : invalidationManager.getInvalidationEvents()) {
                clusterProvider.notify(eventKey, event, true, ClusterProvider.DCNotify.ALL_DCS);
            }
        }
    }


    public void invalidationEventReceived(InvalidationEvent event) {
        InvalidationManager invalidationManager = new InvalidationManager();

        addInvalidationsFromEvent(event, invalidationManager.getModelInvalidations(), invalidationManager);

        getLogger().debugf("[%s] Invalidating %d cache items after received event %s", cache.getCacheManager().getAddress(), invalidationManager.getModelInvalidations().size(), event);

        runInvalidations(invalidationManager);
    }

    protected abstract void addInvalidationsFromEvent(InvalidationEvent event, Set<String> invalidations, InvalidationManager invalidationManager);

}
