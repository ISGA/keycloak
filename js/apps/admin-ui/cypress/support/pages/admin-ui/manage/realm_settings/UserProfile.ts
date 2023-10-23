import Select from "../../../../forms/Select";

export default class UserProfile {
  private userProfileTab = "rs-user-profile-tab";
  private attributesTab = "attributesTab";
  private attributesGroupTab = "attributesGroupTab";
  private jsonEditorTab = "jsonEditorTab";
  private createAttributeButton = "createAttributeBtn";
  private actionsDrpDwn = "actions-dropdown";
  private deleteDrpDwnOption = "deleteDropdownAttributeItem";
  private editDrpDwnOption = "editDropdownAttributeItem";
  private cancelNewAttribute = "attribute-cancel";
  private newAttributeNameInput = "attribute-name";
  private newAttributeDisplayNameInput = "attribute-display-name";
  private newAttributeEnabledWhen = 'input[name="enabledWhen"]';
  private newAttributeCheckboxes = 'input[type="checkbox"]';
  private newAttributeRequiredFor = 'input[name="roles"]';
  private newAttributeRequiredWhen = 'input[name="requiredWhen"]';
  private newAttributeEmptyValidators = ".kc-emptyValidators";
  private newAttributeAnnotationBtn = "annotations-add-row";
  private newAttributeAnnotationKey = "annotations.0.key";
  private newAttributeAnnotationValue = "annotations.0.value";
  private newAttributeRequiredField = "input#kc-required.pf-c-switch__input";
  private newAttributeUserEdit = "user-edit";
  private newAttributeAdminEdit = "admin-edit";
  private newAttributeUserView = "user-view";
  private newAttributeAdminView = "admin-view";
  private validatorRolesList = "#validator";
  private validatorsList = 'tbody [data-label="name"]';
  private saveNewAttributeBtn = "attribute-create";
  private addValidatorBtn = "addValidator";
  private saveValidatorBtn = "save-validator-role-button";
  private removeValidatorBtn = "deleteValidator";
  private deleteValidatorBtn = "confirm";
  private cancelAddingValidatorBtn = "cancel-validator-role-button";
  private cancelRemovingValidatorBtn = "cancel";
  private validatorDialogCloseBtn = 'button[aria-label="Close"]';
  private newAttributesGroupNameInput = "input#kc-name";
  private newAttributesGroupDisplayNameInput = 'input[name="displayHeader"]';
  private saveNewAttributesGroupBtn = "saveGroupBtn";
  private newAnnotationsAddRowBtn = "annotations-remove";

  goToTab() {
    cy.findByTestId(this.userProfileTab).click();
    return this;
  }

  goToAttributesTab() {
    cy.findByTestId(this.attributesTab).click();
    return this;
  }

  goToAttributesGroupTab() {
    cy.findByTestId(this.attributesGroupTab).click();
    return this;
  }

  goToJsonEditorTab() {
    cy.findByTestId(this.jsonEditorTab).click();
    return this;
  }

  createAttributeButtonClick() {
    cy.findByTestId(this.createAttributeButton).click();
    return this;
  }

  selectDropdown() {
    cy.findByTestId(this.actionsDrpDwn).click();
    return this;
  }

  selectDeleteOption() {
    cy.findByTestId(this.deleteDrpDwnOption).click();
    return this;
  }

  selectEditOption() {
    cy.findByTestId(this.editDrpDwnOption).click();
    return this;
  }

  cancelAttributeCreation() {
    cy.findByTestId(this.cancelNewAttribute).click();
    return this;
  }

  createAttribute(name: string, displayName: string) {
    cy.findByTestId(this.newAttributeNameInput).type(name);
    cy.findByTestId(this.newAttributeDisplayNameInput).type(displayName);
    return this;
  }

  checkElementNotInList(name: string) {
    cy.get(this.validatorsList).should("not.contain.text", name);
    return this;
  }

  saveAttributeCreation() {
    cy.findByTestId(this.saveNewAttributeBtn).click();
    return this;
  }

  createAttributeNotRequiredWithPermissions(name: string, displayName: string) {
    cy.findByTestId(this.newAttributeNameInput).type(name);
    cy.findByTestId(this.newAttributeDisplayNameInput).type(displayName);
    cy.get(this.newAttributeEnabledWhen).first().check();
    cy.findByTestId(this.newAttributeUserEdit).first().check({ force: true });
    cy.findByTestId(this.newAttributeAdminEdit).first().check({ force: true });
    cy.findByTestId(this.newAttributeUserView).first().check({ force: true });
    cy.findByTestId(this.newAttributeAdminView).first().check({ force: true });
    return this;
  }

  createAttributeNotRequiredWithoutPermissions(
    name: string,
    displayName: string,
  ) {
    cy.findByTestId(this.newAttributeNameInput).type(name);
    cy.findByTestId(this.newAttributeDisplayNameInput).type(displayName);
    cy.get(this.newAttributeEnabledWhen).first().check();
    return this;
  }

  createAttributeRequiredWithPermissions(name: string, displayName: string) {
    cy.findByTestId(this.newAttributeNameInput).type(name);
    cy.findByTestId(this.newAttributeDisplayNameInput).type(displayName);
    cy.get(this.newAttributeEnabledWhen).first().check();
    cy.get(this.newAttributeRequiredField).first().check({ force: true });
    cy.get(this.newAttributeRequiredWhen).first().check({ force: true });
    cy.findByTestId(this.newAttributeUserEdit).first().check({ force: true });
    cy.findByTestId(this.newAttributeAdminEdit).first().check({ force: true });
    cy.findByTestId(this.newAttributeUserView).first().check({ force: true });
    cy.findByTestId(this.newAttributeAdminView).first().check({ force: true });
    return this;
  }

  createAttributeGroup(name: string, displayName: string) {
    cy.get(this.newAttributesGroupNameInput).type(name);
    cy.get(this.newAttributesGroupDisplayNameInput).type(displayName);
    cy.findAllByTestId(this.newAnnotationsAddRowBtn).click();
    return this;
  }

  saveAttributesGroupCreation() {
    cy.findByTestId(this.saveNewAttributesGroupBtn).click();
    return this;
  }

  selectElementInList(name: string) {
    cy.get(this.validatorsList).contains(name).click();
    return this;
  }

  editAttribute(displayName: string) {
    cy.findByTestId(this.newAttributeDisplayNameInput)
      .click()
      .clear()
      .type(displayName);
    cy.get(this.newAttributeEnabledWhen).first().check();
    cy.get(this.newAttributeCheckboxes).check({ force: true });
    cy.get(this.newAttributeRequiredFor).first().check({ force: true });
    cy.get(this.newAttributeRequiredWhen).first().check();
    cy.get(this.newAttributeEmptyValidators).contains("No validators.");
    cy.findByTestId(this.newAttributeAnnotationBtn).click();
    cy.findByTestId(this.newAttributeAnnotationKey).type("test");
    cy.findByTestId(this.newAttributeAnnotationValue).type("123");
    return this;
  }

  addValidator() {
    cy.findByTestId(this.addValidatorBtn).click();
    Select.selectItem(cy.get(this.validatorRolesList), "email");
    cy.findByTestId(this.saveValidatorBtn).click();
    return this;
  }

  removeValidator() {
    cy.findByTestId(this.removeValidatorBtn).click();
    cy.findByTestId(this.deleteValidatorBtn).click();
    return this;
  }

  cancelAddingValidator() {
    cy.findByTestId(this.addValidatorBtn).click();
    Select.selectItem(cy.get(this.validatorRolesList), "email");
    cy.findByTestId(this.cancelAddingValidatorBtn).click();
    return this;
  }

  cancelRemovingValidator() {
    cy.findByTestId(this.removeValidatorBtn).click();
    cy.findByTestId(this.cancelRemovingValidatorBtn).click();
    return this;
  }

  private textArea() {
    return cy.get(".pf-c-code-editor__code textarea");
  }

  private getText() {
    return this.textArea().get(".view-lines");
  }

  typeJSON(text: string) {
    this.textArea().type(text, { force: true });
    return this;
  }

  shouldHaveText(text: string) {
    this.getText().should("have.text", text);
    return this;
  }

  saveJSON() {
    cy.findAllByTestId("save").click();
    return this;
  }
}
