import type RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import type {
  PartialImportRealmRepresentation,
  PartialImportResponse,
  PartialImportResult,
} from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import type RoleRepresentation from "@keycloak/keycloak-admin-client/lib/defs/roleRepresentation";
import {
  Alert,
  Button,
  ButtonVariant,
  Checkbox,
  DataList,
  DataListCell,
  DataListItem,
  DataListItemCells,
  DataListItemRow,
  Divider,
  Label,
  Modal,
  ModalVariant,
  Stack,
  StackItem,
  Text,
  TextContent,
} from "@patternfly/react-core";
import {
  Select,
  SelectOption,
  SelectOptionObject,
} from "@patternfly/react-core/deprecated";
import {
  ChangeEvent,
  FormEvent,
  MouseEvent as ReactMouseEvent,
  useEffect,
  useState,
} from "react";
import { useTranslation } from "react-i18next";

import { adminClient } from "../admin-client";
import { useAlerts } from "../components/alert/Alerts";
import { JsonFileUpload } from "../components/json-file-upload/JsonFileUpload";
import { KeycloakDataTable } from "../components/table-toolbar/KeycloakDataTable";
import { useRealm } from "../context/realm-context/RealmContext";

export type PartialImportProps = {
  open: boolean;
  toggleDialog: () => void;
};

// An imported JSON file can either be an array of realm objects
// or a single realm object.
type ImportedMultiRealm = RealmRepresentation | RealmRepresentation[];

type NonRoleResource = "users" | "clients" | "groups" | "identityProviders";
type RoleResource = "realmRoles" | "clientRoles";
type Resource = NonRoleResource | RoleResource;

type CollisionOption = "FAIL" | "SKIP" | "OVERWRITE";

type ResourceChecked = { [k in Resource]: boolean };

const INITIAL_RESOURCES: Readonly<ResourceChecked> = {
  users: false,
  clients: false,
  groups: false,
  identityProviders: false,
  realmRoles: false,
  clientRoles: false,
};

export const PartialImportDialog = (props: PartialImportProps) => {
  const { t } = useTranslation();
  const { realm } = useRealm();

  const [importedFile, setImportedFile] = useState<ImportedMultiRealm>();
  const isFileSelected = !!importedFile;
  const [isRealmSelectOpen, setIsRealmSelectOpen] = useState(false);
  const [isCollisionSelectOpen, setIsCollisionSelectOpen] = useState(false);
  const [importInProgress, setImportInProgress] = useState(false);
  const [collisionOption, setCollisionOption] =
    useState<CollisionOption>("FAIL");
  const [targetRealm, setTargetRealm] = useState<RealmRepresentation>({});
  const [importResponse, setImportResponse] = useState<PartialImportResponse>();
  const { addError } = useAlerts();

  const [resourcesToImport, setResourcesToImport] = useState(INITIAL_RESOURCES);
  const isAnyResourceChecked = Object.values(resourcesToImport).some(
    (checked) => checked,
  );

  const resetResourcesToImport = () => {
    setResourcesToImport(INITIAL_RESOURCES);
  };

  const resetInputState = () => {
    setImportedFile(undefined);
    setTargetRealm({});
    setCollisionOption("FAIL");
    resetResourcesToImport();
  };

  // when dialog opens or closes, clear state
  useEffect(() => {
    setImportInProgress(false);
    setImportResponse(undefined);
    resetInputState();
  }, [props.open]);

  const handleFileChange = (value: ImportedMultiRealm) => {
    resetInputState();
    setImportedFile(value);

    if (!Array.isArray(value)) {
      setTargetRealm(value);
    } else if (value.length > 0) {
      setTargetRealm(value[0]);
    }
  };

  const handleRealmSelect = (realm: string | SelectOptionObject) => {
    setTargetRealm(realm as RealmRepresentation);
    setIsRealmSelectOpen(false);
    resetResourcesToImport();
  };

  const handleResourceCheckBox = (
    event: FormEvent<HTMLInputElement>,
    checked: boolean,
  ) => {
    const resource = event.currentTarget.name as Resource;

    setResourcesToImport({
      ...resourcesToImport,
      [resource]: checked,
    });
  };

  const realmSelectOptions = (realms: RealmRepresentation[]) =>
    realms.map((realm) => (
      <SelectOption
        key={realm.id}
        value={realm}
        data-testid={realm.id + "-select-option"}
      >
        {realm.realm || realm.id}
      </SelectOption>
    ));

  const handleCollisionSelect = (
    event: ChangeEvent<Element> | ReactMouseEvent<Element, MouseEvent>,
    option: string | SelectOptionObject,
  ) => {
    setCollisionOption(option as CollisionOption);
    setIsCollisionSelectOpen(false);
  };

  const collisionOptions = () => {
    return [
      <SelectOption key="fail" value="FAIL">
        {t("FAIL")}
      </SelectOption>,
      <SelectOption key="skip" value="SKIP">
        {t("SKIP")}
      </SelectOption>,
      <SelectOption key="overwrite" value="OVERWRITE">
        {t("OVERWRITE")}
      </SelectOption>,
    ];
  };

  const targetHasResources = () => {
    return (
      targetHasResource("users") ||
      targetHasResource("groups") ||
      targetHasResource("clients") ||
      targetHasResource("identityProviders") ||
      targetHasRealmRoles() ||
      targetHasClientRoles()
    );
  };

  const targetHasResource = (resource: NonRoleResource) => {
    const value = targetRealm[resource];
    return value !== undefined && value.length > 0;
  };

  const targetHasRealmRoles = () => {
    const value = targetRealm.roles?.realm;
    return value !== undefined && value.length > 0;
  };

  const targetHasClientRoles = () => {
    const value = targetRealm.roles?.client;
    return value !== undefined && Object.keys(value).length > 0;
  };

  const itemCount = (resource: Resource) => {
    if (!isFileSelected) return 0;

    if (resource === "realmRoles") {
      return targetRealm.roles?.realm?.length ?? 0;
    }

    if (resource === "clientRoles") {
      return targetHasClientRoles()
        ? clientRolesCount(targetRealm.roles!.client!)
        : 0;
    }

    return targetRealm[resource]?.length ?? 0;
  };

  const clientRolesCount = (
    clientRoles: Record<string, RoleRepresentation[]>,
  ) =>
    Object.values(clientRoles).reduce((total, role) => total + role.length, 0);

  const resourceDataListItem = (
    resource: Resource,
    resourceDisplayName: string,
  ) => {
    return (
      <DataListItem aria-labelledby={`${resource}-list-item`}>
        <DataListItemRow>
          <DataListItemCells
            dataListCells={[
              <DataListCell key={resource}>
                <Checkbox
                  id={`${resource}-checkbox`}
                  label={`${itemCount(resource)} ${resourceDisplayName}`}
                  aria-labelledby={`${resource}-checkbox`}
                  name={resource}
                  isChecked={resourcesToImport[resource]}
                  onChange={handleResourceCheckBox}
                  data-testid={resource + "-checkbox"}
                />
              </DataListCell>,
            ]}
          />
        </DataListItemRow>
      </DataListItem>
    );
  };

  const jsonForImport = () => {
    const jsonToImport: PartialImportRealmRepresentation = {
      ifResourceExists: collisionOption,
      id: targetRealm.id,
      realm: targetRealm.realm,
    };

    if (resourcesToImport["users"]) jsonToImport.users = targetRealm.users;
    if (resourcesToImport["groups"]) jsonToImport.groups = targetRealm.groups;
    if (resourcesToImport["identityProviders"])
      jsonToImport.identityProviders = targetRealm.identityProviders;
    if (resourcesToImport["clients"])
      jsonToImport.clients = targetRealm.clients;
    if (resourcesToImport["realmRoles"] || resourcesToImport["clientRoles"]) {
      jsonToImport.roles = targetRealm.roles;
      if (!resourcesToImport["realmRoles"]) delete jsonToImport.roles?.realm;
      if (!resourcesToImport["clientRoles"]) delete jsonToImport.roles?.client;
    }
    return jsonToImport;
  };

  async function doImport() {
    if (importInProgress) return;

    setImportInProgress(true);

    try {
      const importResults = await adminClient.realms.partialImport({
        realm,
        rep: jsonForImport(),
      });
      setImportResponse(importResults);
    } catch (error) {
      addError("realm-settings:importFail", error);
    }

    setImportInProgress(false);
  }

  const importModal = () => {
    return (
      <Modal
        variant={ModalVariant.medium}
        title={t("partialImport")}
        isOpen={props.open}
        onClose={props.toggleDialog}
        actions={[
          <Button
            id="modal-import"
            data-testid="import-button"
            key="import"
            isDisabled={!isAnyResourceChecked}
            onClick={() => {
              doImport();
            }}
          >
            {t("import")}
          </Button>,
          <Button
            id="modal-cancel"
            data-testid="cancel-button"
            key="cancel"
            variant={ButtonVariant.link}
            onClick={() => {
              props.toggleDialog();
            }}
          >
            {t("cancel")}
          </Button>,
        ]}
      >
        <Stack hasGutter>
          <StackItem>
            <TextContent>
              <Text>{t("partialImportHeaderText")}</Text>
            </TextContent>
          </StackItem>
          <StackItem>
            <JsonFileUpload
              id="partial-import-file"
              allowEditingUploadedText
              onChange={handleFileChange}
            />
          </StackItem>

          {isFileSelected && targetHasResources() && (
            <>
              <StackItem>
                <Divider />
              </StackItem>
              {Array.isArray(importedFile) && importedFile.length > 1 && (
                <StackItem>
                  <Text>{t("selectRealm")}:</Text>
                  <Select
                    toggleId="realm-selector"
                    isOpen={isRealmSelectOpen}
                    onToggle={() => setIsRealmSelectOpen(!isRealmSelectOpen)}
                    onSelect={(_, value) => handleRealmSelect(value)}
                    placeholderText={targetRealm.realm || targetRealm.id}
                  >
                    {realmSelectOptions(importedFile)}
                  </Select>
                </StackItem>
              )}
              <StackItem>
                <Text>{t("chooseResources")}:</Text>
                <DataList aria-label={t("resourcesToImport")} isCompact>
                  {targetHasResource("users") &&
                    resourceDataListItem("users", t("users"))}
                  {targetHasResource("groups") &&
                    resourceDataListItem("groups", t("groups"))}
                  {targetHasResource("clients") &&
                    resourceDataListItem("clients", t("clients"))}
                  {targetHasResource("identityProviders") &&
                    resourceDataListItem(
                      "identityProviders",
                      t("identityProviders"),
                    )}
                  {targetHasRealmRoles() &&
                    resourceDataListItem("realmRoles", t("realmRoles"))}
                  {targetHasClientRoles() &&
                    resourceDataListItem("clientRoles", t("clientRoles"))}
                </DataList>
              </StackItem>
              <StackItem>
                <Text>{t("selectIfResourceExists")}:</Text>
                <Select
                  isOpen={isCollisionSelectOpen}
                  direction="up"
                  onToggle={() => {
                    setIsCollisionSelectOpen(!isCollisionSelectOpen);
                  }}
                  onSelect={handleCollisionSelect}
                  placeholderText={t(collisionOption)}
                >
                  {collisionOptions()}
                </Select>
              </StackItem>
            </>
          )}
        </Stack>
      </Modal>
    );
  };

  const importCompleteMessage = () => {
    return `${t("importAdded", {
      count: importResponse?.added,
    })}  ${t("importSkipped", {
      count: importResponse?.skipped,
    })} ${t("importOverwritten", {
      count: importResponse?.overwritten,
    })}`;
  };

  const loader = async (first = 0, max = 15) => {
    if (!importResponse) {
      return [];
    }

    const last = Math.min(first + max, importResponse.results.length);

    return importResponse.results.slice(first, last);
  };

  const ActionLabel = (importRecord: PartialImportResult) => {
    switch (importRecord.action) {
      case "ADDED":
        return (
          <Label key={importRecord.id} color="green">
            {t("added")}
          </Label>
        );
      case "SKIPPED":
        return (
          <Label key={importRecord.id} color="orange">
            {t("skipped")}
          </Label>
        );
      case "OVERWRITTEN":
        return (
          <Label key={importRecord.id} color="purple">
            {t("overwritten")}
          </Label>
        );
      default:
        return "";
    }
  };

  const TypeRenderer = (importRecord: PartialImportResult) => {
    const typeMap = new Map([
      ["CLIENT", t("clients")],
      ["REALM_ROLE", t("realmRoles")],
      ["USER", t("users")],
      ["CLIENT_ROLE", t("clientRoles")],
      ["IDP", t("identityProviders")],
      ["GROUP", t("groups")],
    ]);

    return <span>{typeMap.get(importRecord.resourceType)}</span>;
  };

  const importCompletedModal = () => {
    return (
      <Modal
        variant={ModalVariant.medium}
        title={t("partialImport")}
        isOpen={props.open}
        onClose={props.toggleDialog}
        actions={[
          <Button
            id="modal-close"
            data-testid="close-button"
            key="close"
            variant={ButtonVariant.primary}
            onClick={() => {
              props.toggleDialog();
            }}
          >
            {t("close")}
          </Button>,
        ]}
      >
        <Alert variant="success" isInline title={importCompleteMessage()} />
        <KeycloakDataTable
          loader={loader}
          isPaginated
          ariaLabelKey="realm-settings:partialImport"
          columns={[
            {
              name: "action",
              displayKey: "action",
              cellRenderer: ActionLabel,
            },
            {
              name: "resourceType",
              displayKey: "type",
              cellRenderer: TypeRenderer,
            },
            {
              name: "resourceName",
              displayKey: "name",
            },
            {
              name: "id",
              displayKey: "id",
            },
          ]}
        />
      </Modal>
    );
  };

  if (!importResponse) {
    return importModal();
  }

  return importCompletedModal();
};
