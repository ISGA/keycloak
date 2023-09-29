const expect = chai.expect;
export default class RoleMappingTab {
  private type = "client";
  private serviceAccountTab = "serviceAccountTab";
  private scopeTab = "scopeTab";
  private assignEmptyRoleBtn = (type: string) =>
    `no-roles-for-this-${type}-empty-action`;
  private assignRoleBtn = "assignRole";
  private unAssignBtn = "unAssignRole";
  private unAssignDrpDwnBtn = '.pf-c-table__action li button[role="menuitem"]';
  private assignBtn = "assign";
  private hideInheritedRolesBtn = "#hideInheritedRoles";
  private assignedRolesTable = "assigned-roles";
  private namesColumn = 'td[data-label="Name"]:visible';
  private roleMappingTab = "role-mapping-tab";
  private filterTypeDropdown = "filter-type-dropdown";
  private filterTypeDropdownItem = "roles";
  private addRoleTable = 'input[placeholder="Search by role name"]';

  constructor(type: string) {
    this.type = type;
  }

  goToServiceAccountTab() {
    cy.findByTestId(this.serviceAccountTab).click();
    return this;
  }

  goToScopeTab() {
    cy.findByTestId(this.scopeTab).click();
    return this;
  }

  assignRole(notEmpty = true) {
    cy.findByTestId(
      notEmpty ? this.assignEmptyRoleBtn(this.type) : this.assignRoleBtn,
    ).click();
    return this;
  }

  assign() {
    cy.findByTestId(this.assignBtn).click();
    return this;
  }

  unAssign() {
    cy.findByTestId(this.unAssignBtn).click();
    return this;
  }

  unAssignFromDropdown() {
    cy.get(this.unAssignDrpDwnBtn).click();
    return this;
  }

  hideInheritedRoles() {
    cy.get(this.hideInheritedRolesBtn).check();
    return this;
  }

  unhideInheritedRoles() {
    cy.get(this.hideInheritedRolesBtn).uncheck({ force: true });
    return this;
  }

  selectRow(name: string, modal = false) {
    cy.get(modal ? ".pf-c-modal-box " : "" + this.namesColumn)
      .contains(name)
      .parent()
      .within(() => {
        cy.get("input").click();
      });
    return this;
  }

  checkRoles(roleNames: string[], exist = true) {
    if (roleNames.length) {
      cy.findByTestId(this.assignedRolesTable)
        .get(this.namesColumn)
        .should((roles) => {
          for (let index = 0; index < roleNames.length; index++) {
            const roleName = roleNames[index];

            if (exist) {
              expect(roles).to.contain(roleName);
            } else {
              expect(roles).not.to.contain(roleName);
            }
          }
        });
    } else {
      cy.findByTestId(this.assignedRolesTable).should("not.exist");
    }
    return this;
  }

  goToRoleMappingTab() {
    cy.findByTestId(this.roleMappingTab).click();
    return this;
  }

  addClientRole(roleName: string) {
    cy.findByTestId(this.assignRoleBtn).click();
    cy.findByTestId(this.filterTypeDropdown).click();
    cy.findByTestId(this.filterTypeDropdownItem).click();

    cy.get(this.addRoleTable).type(`${roleName}{enter}`);

    // cy.findByTestId(this.addAssociatedRolesModalButton).click();

    // cy.contains("Users in role").click();
    // cy.findByTestId(this.usersPage).should("exist");
  }

  // addClientRole(roleName: string) {
  //   cy.findByTestId("assignRole").click({ force: true });
  //   cy.findByTestId("filter-type-dropdown").click();
  //   cy.findByTestId("roles").click();
  //   cy.get('input[placeholder="Search by role name"]').type(
  //     "view-groups{enter}",
  //   );
  // }
}
