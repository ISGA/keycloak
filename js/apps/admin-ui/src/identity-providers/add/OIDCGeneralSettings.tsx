import { FormGroup, ValidatedOptions } from "@patternfly/react-core";
import { useFormContext, useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router-dom";
import { HelpItem } from "ui-shared";

import { DisplayOrder } from "../component/DisplayOrder";
import { RedirectUrl } from "../component/RedirectUrl";
import { TextField } from "../component/TextField";
import type { IdentityProviderParams } from "../routes/IdentityProvider";

export const OIDCGeneralSettings = () => {
  const { t } = useTranslation();
  const { tab } = useParams<IdentityProviderParams>();

  const {
    register,
    control,
    formState: { errors },
  } = useFormContext();

  const alias = useWatch({ control, name: "alias" });

  return (
    <>
      <RedirectUrl id={alias} />

      <FormGroup
        label={t("alias")}
        labelIcon={
          <HelpItem
            helpText={t("identity-providers-help:alias")}
            fieldLabelId="identity-providers:alias"
          />
        }
        fieldId="alias"
        isRequired
        validated={
          errors.alias ? ValidatedOptions.error : ValidatedOptions.default
        }
        helperTextInvalid={t("required")}
      >
        <TextInput
          isReadOnly={tab === "settings"}
          isRequired
          id="alias"
          data-testid="alias"
          validated={
            errors.alias ? ValidatedOptions.error : ValidatedOptions.default
          }
          {...register("alias", { required: true })}
        />
      </FormGroup>

      <TextField field="displayName" label="displayName" />
      <DisplayOrder />
    </>
  );
};
