import IdentityProviderRepresentation from "@keycloak/keycloak-admin-client/lib/defs/identityProviderRepresentation";
import { FormGroup, ValidatedOptions } from "@patternfly/react-core";
import { useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { HelpItem } from "ui-shared";

import { PasswordInput } from "../../components/password-input/PasswordInput";

export const ClientIdSecret = ({
  secretRequired = true,
  create = true,
}: {
  secretRequired?: boolean;
  create?: boolean;
}) => {
  const { t } = useTranslation();

  const {
    register,
    formState: { errors },
  } = useFormContext<IdentityProviderRepresentation>();

  return (
    <>
      <FormGroup
        label={t("clientId")}
        labelIcon={
          <HelpItem
            helpText={t("clientIdHelp")}
            fieldLabelId="identity-providers:clientId"
          />
        }
        fieldId="kc-client-id"
        isRequired
        validated={
          errors.config?.clientId
            ? ValidatedOptions.error
            : ValidatedOptions.default
        }
        helperTextInvalid={t("required")}
      >
        <TextInput
          isRequired
          id="kc-client-id"
          data-testid="clientId"
          {...register("config.clientId", { required: true })}
        />
      </FormGroup>
      <FormGroup
        label={t("clientSecret")}
        labelIcon={
          <HelpItem
            helpText={t("clientSecretHelp")}
            fieldLabelId="identity-providers:clientSecret"
          />
        }
        fieldId="kc-client-secret"
        isRequired={secretRequired}
        validated={
          errors.config?.clientSecret
            ? ValidatedOptions.error
            : ValidatedOptions.default
        }
        helperTextInvalid={t("required")}
      >
        {create ? (
          <PasswordInput
            isRequired={secretRequired}
            id="kc-client-secret"
            data-testid="clientSecret"
            {...register("config.clientSecret", { required: secretRequired })}
          />
        ) : (
          <TextInput
            isRequired={secretRequired}
            type="password"
            id="kc-client-secret"
            data-testid="clientSecret"
            {...register("config.clientSecret", { required: secretRequired })}
          />
        )}
      </FormGroup>
    </>
  );
};
