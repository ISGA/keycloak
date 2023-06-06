import type ClientRepresentation from "@keycloak/keycloak-admin-client/lib/defs/clientRepresentation";
import type { ClientQuery } from "@keycloak/keycloak-admin-client/lib/resources/clients";
import { FormGroup } from "@patternfly/react-core";
import {
  Select,
  SelectOption,
  SelectVariant,
} from "@patternfly/react-core/deprecated";
import { useState } from "react";
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { HelpItem } from "ui-shared";

import { adminClient } from "../../admin-client";
import { useFetch } from "../../utils/useFetch";
import type { ComponentProps } from "../dynamic/components";

type ClientSelectProps = ComponentProps & {
  namespace: string;
  required?: boolean;
};

export const ClientSelect = ({
  name,
  label,
  helpText,
  defaultValue,
  namespace,
  isDisabled = false,
  required = false,
}: ClientSelectProps) => {
  const { t } = useTranslation(namespace);
  const {
    control,
    // formState: { errors },
  } = useFormContext();

  const [open, setOpen] = useState(false);
  const [clients, setClients] = useState<ClientRepresentation[]>([]);
  const [search, setSearch] = useState("");

  useFetch(
    () => {
      const params: ClientQuery = {
        max: 20,
      };
      if (search) {
        params.clientId = search;
        params.search = true;
      }
      return adminClient.clients.find(params);
    },
    (clients) => setClients(clients),
    [search]
  );

  const convert = (clients: ClientRepresentation[]) => [
    <SelectOption key="empty" value="">
      {t("common:none")}
    </SelectOption>,
    ...clients.map((option) => (
      <SelectOption key={option.id} value={option.clientId} />
    )),
  ];

  return (
    <FormGroup
      label={t(label!)}
      isRequired={required}
      labelIcon={
        <HelpItem
          helpText={t(helpText!)}
          fieldLabelId={`${namespace}:${label}`}
        />
      }
      fieldId={name!}
      // TODO: Use FormHelperText, HelperText, and HelperTextItem directly inside children. helperText, // helperTextInvalid and validated props have been removed.
      // validated={errors[name!] ? "error" : "default"}
      // helperTextInvalid={t("common:required")}
    >
      <Controller
        name={name!}
        defaultValue={defaultValue || ""}
        control={control}
        rules={required ? { required: true } : {}}
        render={({ field }) => (
          <Select
            toggleId={name}
            variant={SelectVariant.typeahead}
            onToggle={(_event, open) => setOpen(open)}
            isOpen={open}
            isDisabled={isDisabled}
            selections={field.value}
            onFilter={(_, value) => {
              setSearch(value);
              return convert(clients);
            }}
            onSelect={(_, value) => {
              field.onChange(value.toString());
              setOpen(false);
            }}
            typeAheadAriaLabel={t(label!)}
          >
            {convert(clients)}
          </Select>
        )}
      />
    </FormGroup>
  );
};
