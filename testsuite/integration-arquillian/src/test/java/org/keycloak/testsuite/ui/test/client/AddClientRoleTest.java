package org.keycloak.testsuite.ui.test.client;

import org.jboss.arquillian.graphene.findby.FindByJQuery;
import org.jboss.arquillian.graphene.page.Page;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.keycloak.testsuite.ui.AbstractKeyCloakUITest;
import org.keycloak.testsuite.ui.fragment.FlashMessage;
import org.keycloak.testsuite.ui.fragment.RoleMappings;
import org.keycloak.testsuite.ui.model.Client;
import org.keycloak.testsuite.ui.model.Role;
import org.keycloak.testsuite.ui.model.User;
import org.keycloak.testsuite.ui.page.settings.ClientPage;
import org.keycloak.testsuite.ui.page.settings.RolesPage;
import org.keycloak.testsuite.ui.page.settings.user.UserPage;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;
import static org.openqa.selenium.By.linkText;

/**
 * Created by fkiss.
 */
public class AddClientRoleTest extends AbstractKeyCloakUITest<ClientPage> {

    @Page
    private UserPage userPage;

    @Page
    private RolesPage rolesPage;

    @Page
    private RoleMappings roleMappings;

    @FindByJQuery(".alert")
    private FlashMessage flashMessage;

    @Before
    public void beforeTestAddClientRole() {
        navigation.clients();
    }

    @Ignore
    @Test
    public void testAddClientRole() {
        Client newClient = new Client("test-client1", "http://example.com/*");
        Role newRole = new Role("client-role");

        page.addClient(newClient);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.rolesTab(newClient.getName());
        rolesPage.addRole(newRole);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.clients();
        page.findClient(newClient.getName());
        page.goToClient(newClient);
        navigation.rolesTab(newClient.getName());
        assertNotNull(driver.findElement(linkText(newRole.getName())));

        navigation.clients();
        page.deleteClient(newClient.getName());
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        assertNull(page.findClient(newClient.getName()));
    }

    @Ignore //KEYCLOAK-1497
    @Test
    public void testAddClientRoleToUser() {
        Client newClient = new Client("test-client2", "http://example.com/*");
        Role newRole = new Role("client-role2");
        String testUsername = "test-user2";
        User newUser = new User(testUsername, "pass");

        page.addClient(newClient);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.rolesTab(newClient.getName());
        rolesPage.addRole(newRole);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.clients();
        page.findClient(newClient.getName());
        page.goToClient(newClient);
        navigation.rolesTab(newClient.getName());
        assertNotNull(driver.findElement(linkText(newRole.getName())));

        navigation.users();
        userPage.addUser(newUser);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        navigation.users();
        userPage.findUser(testUsername);
        userPage.goToUser(testUsername);

        navigation.roleMappings(testUsername);
        roleMappings.selectClientRole(newClient.getName());
        roleMappings.addAvailableClientRole(newRole.getName());
        //flashMessage.waitUntilPresent();
        //assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        //KEYCLOAK-1497
        assertTrue(roleMappings.isAssignedClientRole(newRole.getName()));

        navigation.users();
        userPage.deleteUser(testUsername);

        navigation.clients();
        page.deleteClient(newClient.getName());
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        assertNull(page.findClient(newClient.getName()));
    }


    @Ignore //KEYCLOAK-1496, KEYCLOAK-1497
    @Test
    public void testAddCompositeRealmClientRoleToUser() {
        Client newClient = new Client("test-client3", "http://example.com/*");
        Role clientCompositeRole = new Role("client-composite-role");
        String testUsername = "test-user3";
        User newUser = new User(testUsername, "pass");
        Role subRole1 = new Role("sub-role1");
        Role subRole2 = new Role("sub-role2");
        List<Role> roles = new ArrayList<>();
        clientCompositeRole.setComposite(true);
        roles.add(subRole1);
        roles.add(subRole2);

        //create sub-roles
        navigation.roles();
        for (Role role : roles) {
            rolesPage.addRole(role);
            flashMessage.waitUntilPresent();
            assertTrue(flashMessage.getText(), flashMessage.isSuccess());
            navigation.roles();
            assertEquals(role.getName(), rolesPage.findRole(role.getName()).getName());
        }

        //create client
        navigation.clients();
        page.addClient(newClient);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //add client role
        navigation.rolesTab(newClient.getName());
        rolesPage.addRole(clientCompositeRole);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //add realm composite roles
        rolesPage.setCompositeRole(clientCompositeRole);
        roleMappings.addAvailableRole(subRole1.getName(), subRole2.getName());
        //flashMessage.waitUntilPresent();
        //assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        //KEYCLOAK-1497

        //create user
        navigation.users();
        userPage.addUser(newUser);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //add client role to user and verify
        navigation.users();
        userPage.findUser(testUsername);
        userPage.goToUser(testUsername);

        navigation.roleMappings(testUsername);
        roleMappings.selectClientRole(newClient.getName());
        roleMappings.addAvailableClientRole(clientCompositeRole.getName());
        //flashMessage.waitUntilPresent();
        //assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        //KEYCLOAK-1497
        assertTrue(roleMappings.isAssignedClientRole(clientCompositeRole.getName()));
        assertTrue(roleMappings.checkIfEffectiveRealmRolesAreComplete(subRole1, subRole2)); //KEYCLOAK-1496
        assertTrue(roleMappings.checkIfEffectiveClientRolesAreComplete(clientCompositeRole));

        //delete everything
        navigation.users();
        userPage.deleteUser(testUsername);

        navigation.roles();
        rolesPage.deleteRole(subRole1);
        navigation.roles();
        rolesPage.deleteRole(subRole2);

        navigation.clients();
        page.deleteClient(newClient.getName());
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        assertNull(page.findClient(newClient.getName()));
    }

    @Ignore //KEYCLOAK-1504, KEYCLOAK-1497
    @Test
    public void testAddCompositeClientRoleToUser() {
        Client newClient = new Client("test-client4", "http://example.com/*");
        Role clientCompositeRole = new Role("client-composite-role2");
        String testUsername = "test-user4";
        User newUser = new User(testUsername, "pass");
        Role subRole1 = new Role("client-sub-role1");
        Role subRole2 = new Role("client-sub-role2");
        List<Role> roles = new ArrayList<>();
        clientCompositeRole.setComposite(true);
        roles.add(clientCompositeRole);
        roles.add(subRole1);
        roles.add(subRole2);

        //create client
        page.addClient(newClient);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //create sub-roles
        navigation.rolesTab(newClient.getName());
        for (Role role : roles) {
            navigation.clients();
            page.goToClient(newClient);
            navigation.rolesTab(newClient.getName());
            rolesPage.addRole(role);
            flashMessage.waitUntilPresent();
            assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        }

        //add client composite roles
        navigation.clients();
        page.goToClient(newClient);
        navigation.rolesTab(newClient.getName());
        rolesPage.goToRole(clientCompositeRole);
        rolesPage.setCompositeRole(clientCompositeRole);
        roleMappings.selectClientRole(newClient.getName());
        roleMappings.addAvailableClientRole(subRole1.getName(), subRole2.getName());
        //flashMessage.waitUntilPresent();
        //assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        //KEYCLOAK-1504, KEYCLOAK-1497

        //create user
        navigation.users();
        userPage.addUser(newUser);
        flashMessage.waitUntilPresent();
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());

        //add client role to user and verify
        navigation.users();
        userPage.findUser(testUsername);
        userPage.goToUser(testUsername);

        navigation.roleMappings(testUsername);
        roleMappings.selectClientRole(newClient.getName());
        roleMappings.addAvailableClientRole(clientCompositeRole.getName());
        assertTrue(roleMappings.isAssignedClientRole(clientCompositeRole.getName()));
        assertTrue(roleMappings.checkIfEffectiveClientRolesAreComplete(clientCompositeRole, subRole1, subRole2));

        //delete everything
        navigation.users();
        userPage.deleteUser(testUsername);

        navigation.roles();
        rolesPage.deleteRole(subRole1);
        navigation.roles();
        rolesPage.deleteRole(subRole2);

        navigation.clients();
        page.deleteClient(newClient.getName());
        assertTrue(flashMessage.getText(), flashMessage.isSuccess());
        assertNull(page.findClient(newClient.getName()));
    }
}
