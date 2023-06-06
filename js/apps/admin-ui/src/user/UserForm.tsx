import type GroupRepresentation from "@keycloak/keycloak-admin-client/lib/defs/groupRepresentation";
import type RealmRepresentation from "@keycloak/keycloak-admin-client/lib/defs/realmRepresentation";
import type UserRepresentation from "@keycloak/keycloak-admin-client/lib/defs/userRepresentation";
import {
  ActionGroup,
  AlertVariant,
  Button,
  Chip,
  ChipGroup,
  FormGroup,
  InputGroup,
  Switch,
  InputGroupItem,
} from "@patternfly/react-core";
import { useState } from "react";
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";
import { HelpItem } from "ui-shared";

import { adminClient } from "../admin-client";
import { useAlerts } from "../components/alert/Alerts";
import { FormAccess } from "../components/form/FormAccess";
import { GroupPickerDialog } from "../components/group/GroupPickerDialog";
import { KeycloakTextInput } from "../components/keycloak-text-input/KeycloakTextInput";
import { useAccess } from "../context/access/Access";
import { useRealm } from "../context/realm-context/RealmContext";
import { emailRegexPattern } from "../util";
import { useFetch } from "../utils/useFetch";
import useFormatDate from "../utils/useFormatDate";
import useIsFeatureEnabled, { Feature } from "../utils/useIsFeatureEnabled";
import { FederatedUserLink } from "./FederatedUserLink";
import { UserProfileFields } from "./UserProfileFields";
import { RequiredActionMultiSelect } from "./user-credentials/RequiredActionMultiSelect";

export type BruteForced = {
  isBruteForceProtected?: boolean;
  isLocked?: boolean;
};

export type UserFormProps = {
  user?: UserRepresentation;
  bruteForce?: BruteForced;
  save: (user: UserRepresentation) => void;
  onGroupsUpdate?: (groups: GroupRepresentation[]) => void;
};

const EmailVerified = () => {
  const { t } = useTranslation("users");
  const { control } = useFormContext();
  return (
    <FormGroup
      label={t("emailVerified")}
      fieldId="kc-email-verified"
      // TODO: Use FormHelperText, HelperText, and HelperTextItem directly inside children. helperText, // helperTextInvalid and validated props have been removed.
      // helperTextInvalid={t("common:required")}
      labelIcon={
        <HelpItem
          helpText={t("users-help:emailVerified")}
          fieldLabelId="users:emailVerified"
        />
      }
    >
      <Controller
        name="emailVerified"
        defaultValue={false}
        control={control}
        render={({ field }) => (
          <Switch
            data-testid="email-verified-switch"
            id="kc-user-email-verified"
            onChange={(_event, value) => field.onChange(value)}
            isChecked={field.value}
            label={t("common:yes")}
            labelOff={t("common:no")}
          />
        )}
      />
    </FormGroup>
  );
};

export const UserForm = ({
  user,
  bruteForce: { isBruteForceProtected, isLocked } = {
    isBruteForceProtected: false,
    isLocked: false,
  },
  save,
  onGroupsUpdate,
}: UserFormProps) => {
  const { t } = useTranslation("users");
  const { realm: realmName } = useRealm();
  const formatDate = useFormatDate();
  const isFeatureEnabled = useIsFeatureEnabled();

  const navigate = useNavigate();
  const { addAlert, addError } = useAlerts();
  const { hasAccess } = useAccess();
  const isManager = hasAccess("manage-users");

  const {
    handleSubmit,
    register,
    watch,
    control,
    reset,
    formState: { errors },
  } = useFormContext();
  const watchUsernameInput = watch("username");
  const [selectedGroups, setSelectedGroups] = useState<GroupRepresentation[]>(
    []
  );
  const [open, setOpen] = useState(false);
  const [locked, setLocked] = useState(isLocked);
  const [realm, setRealm] = useState<RealmRepresentation>();

  useFetch(
    () => adminClient.realms.findOne({ realm: realmName }),
    (realm) => {
      if (!realm) {
        throw new Error(t("common:notFound"));
      }
      setRealm(realm);
    },
    []
  );

  const unLockUser = async () => {
    try {
      await adminClient.attackDetection.del({ id: user!.id! });
      addAlert(t("unlockSuccess"), AlertVariant.success);
    } catch (error) {
      addError("users:unlockError", error);
    }
  };

  const deleteItem = (id: string) => {
    setSelectedGroups(selectedGroups.filter((item) => item.name !== id));
    onGroupsUpdate?.(selectedGroups);
  };

  const addChips = async (groups: GroupRepresentation[]): Promise<void> => {
    setSelectedGroups([...selectedGroups!, ...groups]);
    onGroupsUpdate?.([...selectedGroups!, ...groups]);
  };

  const addGroups = async (groups: GroupRepresentation[]): Promise<void> => {
    const newGroups = groups;

    newGroups.forEach(async (group) => {
      try {
        await adminClient.users.addToGroup({
          id: user!.id!,
          groupId: group.id!,
        });
        addAlert(t("users:addedGroupMembership"), AlertVariant.success);
      } catch (error) {
        addError("users:addedGroupMembershipError", error);
      }
    });
  };

  const toggleModal = () => {
    setOpen(!open);
  };

  const isUserProfileEnabled =
    isFeatureEnabled(Feature.DeclarativeUserProfile) &&
    realm?.attributes?.userProfileEnabled === "true";

  return (
    <FormAccess
      isHorizontal
      onSubmit={handleSubmit(save)}
      role="query-users"
      fineGrainedAccess={user?.access?.manage}
      className="pf-u-mt-lg"
    >
      {open && (
        <GroupPickerDialog
          type="selectMany"
          text={{
            title: "users:selectGroups",
            ok: "users:join",
          }}
          canBrowse={isManager}
          onConfirm={(groups) => {
            user?.id ? addGroups(groups || []) : addChips(groups || []);
            setOpen(false);
          }}
          onClose={() => setOpen(false)}
          filterGroups={selectedGroups}
        />
      )}
      {isUserProfileEnabled && <EmailVerified />}
      {user?.id && (
        <>
          <FormGroup label={t("common:id")} fieldId="kc-id" isRequired>
            <KeycloakTextInput
              id={user.id}
              aria-label={t("userID")}
              value={user.id}
              type="text"
              readOnly
            />
          </FormGroup>
          <FormGroup label={t("createdAt")} fieldId="kc-created-at" isRequired>
            <KeycloakTextInput
              value={formatDate(new Date(user.createdTimestamp!))}
              type="text"
              id="kc-created-at"
              aria-label={t("createdAt")}
              name="createdTimestamp"
              readOnly
            />
          </FormGroup>
        </>
      )}
      <RequiredActionMultiSelect
        name="requiredActions"
        label="requiredUserActions"
        help="users-help:requiredUserActions"
      />
      {(user?.federationLink || user?.origin) && (
        <FormGroup
          label={t("federationLink")}
          labelIcon={
            <HelpItem
              helpText={t("users-help:federationLink")}
              fieldLabelId="users:federationLink"
            />
          }
        >
          <FederatedUserLink user={user} />
        </FormGroup>
      )}
      {isUserProfileEnabled ? (
        <UserProfileFields />
      ) : (
        <>
          {!realm?.registrationEmailAsUsername && (
            <FormGroup
              label={t("username")}
              fieldId="kc-username"
              isRequired
              // TODO: Use FormHelperText, HelperText, and HelperTextItem directly inside children. helperText, // helperTextInvalid and validated props have been removed.
              // validated={errors.username ? "error" : "default"}
              // helperTextInvalid={t("common:required")}
            >
              <KeycloakTextInput
                id="kc-username"
                readOnlyVariant={
                  !!user?.id &&
                  !realm?.editUsernameAllowed &&
                  realm?.editUsernameAllowed !== undefined
                    ? "default"
                    : undefined
                }
                {...register("username")}
              />
            </FormGroup>
          )}
          <FormGroup
            label={t("email")}
            fieldId="kc-email"
            // TODO: Use FormHelperText, HelperText, and HelperTextItem directly inside children. helperText, // helperTextInvalid and validated props have been removed.
            // validated={errors.email ? "error" : "default"}
            // helperTextInvalid={t("users:emailInvalid")}
          >
            <KeycloakTextInput
              type="email"
              id="kc-email"
              data-testid="email-input"
              {...register("email", {
                pattern: emailRegexPattern,
              })}
            />
          </FormGroup>
          <EmailVerified />
          <FormGroup
            label={t("firstName")}
            fieldId="kc-firstName"
            // helperTextInvalid={t("common:required")}
          >
            <KeycloakTextInput
              data-testid="firstName-input"
              validated={errors.firstName ? "error" : "default"}
              id="kc-firstName"
              {...register("firstName")}
            />
          </FormGroup>
          <FormGroup label={t("lastName")} fieldId="kc-lastName">
            <KeycloakTextInput
              data-testid="lastName-input"
              validated={errors.lastName ? "error" : "default"}
              id="kc-lastname"
              aria-label={t("lastName")}
              {...register("lastName")}
            />
          </FormGroup>
        </>
      )}
      {isBruteForceProtected && (
        <FormGroup
          label={t("temporaryLocked")}
          fieldId="temporaryLocked"
          labelIcon={
            <HelpItem
              helpText={t("users-help:temporaryLocked")}
              fieldLabelId="users:temporaryLocked"
            />
          }
        >
          <Switch
            data-testid="user-locked-switch"
            id="temporaryLocked"
            onChange={(_event, value) => {
              unLockUser();
              setLocked(value);
            }}
            isChecked={locked}
            isDisabled={!locked}
            label={t("common:on")}
            labelOff={t("common:off")}
          />
        </FormGroup>
      )}
      {!user?.id && (
        <FormGroup
          label={t("common:groups")}
          fieldId="kc-groups"
          // TODO: Use FormHelperText, HelperText, and HelperTextItem directly inside children. // helperTextInvalid and validated props have been removed.
          // validated={errors.requiredActions ? "error" : "default"}
          // helperTextInvalid={t("common:required")}
          labelIcon={
            <HelpItem helpText={t("users-help:groups")} fieldLabelId="groups" />
          }
        >
          <Controller
            name="groups"
            defaultValue={[]}
            control={control}
            render={() => (
              <InputGroup>
                <InputGroupItem>
                  <ChipGroup categoryName={" "}>
                    {selectedGroups.map((currentChip) => (
                      <Chip
                        key={currentChip.id}
                        onClick={() => deleteItem(currentChip.name!)}
                      >
                        {currentChip.path}
                      </Chip>
                    ))}
                  </ChipGroup>
                </InputGroupItem>
                <InputGroupItem>
                  <Button
                    id="kc-join-groups-button"
                    onClick={toggleModal}
                    variant="secondary"
                    data-testid="join-groups-button"
                  >
                    {t("users:joinGroups")}
                  </Button>
                </InputGroupItem>
              </InputGroup>
            )}
          />
        </FormGroup>
      )}

      <ActionGroup>
        <Button
          data-testid={!user?.id ? "create-user" : "save-user"}
          isDisabled={
            !user?.id &&
            !watchUsernameInput &&
            !realm?.registrationEmailAsUsername
          }
          variant="primary"
          type="submit"
        >
          {user?.id ? t("common:save") : t("common:create")}
        </Button>
        <Button
          data-testid="cancel-create-user"
          onClick={() =>
            user?.id ? reset(user) : navigate(`/${realmName}/users`)
          }
          variant="link"
        >
          {user?.id ? t("common:revert") : t("common:cancel")}
        </Button>
      </ActionGroup>
    </FormAccess>
  );
};
