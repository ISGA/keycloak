package org.keycloak.testsuite.console.users;

import javax.ws.rs.core.Response;
import org.junit.Ignore;
import org.junit.Test;
import org.keycloak.representations.idm.UserRepresentation;
import static org.keycloak.testsuite.admin.ApiUtil.getCreatedId;
import static org.keycloak.testsuite.util.SeleniumUtils.pause;
import org.keycloak.testsuite.util.Timer;

/**
 *
 * @author tkyjovsk
 */
public class UsersTest extends AbstractUserTest {
    
    public void createTestUsers(String usernamePrefix, int count) {
//        Timer.time();
        for (int i = 0; i < count; i++) {
            String username = String.format("%s%03d", usernamePrefix, i);
            UserRepresentation u = createUserRepresentation(
                    username,
                    username + "@email.test",
                    "First",
                    "Last",
                    true);
            Timer.time();
            Response r = testRealmResource().users().create(u);
            String id = getCreatedId(r);
            r.close();
            Timer.time("create user");
        }
//        Timer.time("create " + count + " users");
    }
    
    @Test
    @Ignore
    public void usersPagination() {
        createTestUsers("test_user_", 100);
        
        users.navigateTo();
        users.table().viewAllUsers();
        pause(120000);
    }
    
}
