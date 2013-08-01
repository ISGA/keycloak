package org.keycloak.services.models.picketlink.mappings;

import org.picketlink.idm.jpa.annotations.AttributeValue;
import org.picketlink.idm.jpa.annotations.OwnerReference;
import org.picketlink.idm.jpa.annotations.entity.IdentityManaged;
import org.picketlink.idm.jpa.model.sample.simple.AccountTypeEntity;
import org.picketlink.idm.jpa.model.sample.simple.PartitionTypeEntity;

import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.OneToOne;
import java.io.Serializable;

/**
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
@IdentityManaged(ResourceData.class)
@Entity
public class ResourceEntity implements Serializable {
    @OneToOne
    @Id
    @OwnerReference
    private PartitionTypeEntity partitionTypeEntity;

    @AttributeValue
    private String realmName;
    @AttributeValue
    private boolean enabled;
    @AttributeValue
    private boolean surrogateAuthRequired;
    @AttributeValue
    private String managementUrl;

    @OneToOne
    @AttributeValue
    AccountTypeEntity resourceUser;


    public PartitionTypeEntity getPartitionTypeEntity() {
        return partitionTypeEntity;
    }

    public void setPartitionTypeEntity(PartitionTypeEntity partitionTypeEntity) {
        this.partitionTypeEntity = partitionTypeEntity;
    }

    public String getRealmName() {
        return realmName;
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSurrogateAuthRequired() {
        return surrogateAuthRequired;
    }

    public void setSurrogateAuthRequired(boolean surrogateAuthRequired) {
        this.surrogateAuthRequired = surrogateAuthRequired;
    }

    public String getManagementUrl() {
        return managementUrl;
    }

    public void setManagementUrl(String managementUrl) {
        this.managementUrl = managementUrl;
    }

    public AccountTypeEntity getResourceUser() {
        return resourceUser;
    }

    public void setResourceUser(AccountTypeEntity resourceUser) {
        this.resourceUser = resourceUser;
    }
}
