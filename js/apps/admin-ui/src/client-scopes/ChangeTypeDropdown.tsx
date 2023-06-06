import { AlertVariant } from "@patternfly/react-core";
import { Select } from "@patternfly/react-core/deprecated";
import { useState } from "react";
import { useTranslation } from "react-i18next";

import type { Row } from "../clients/scopes/ClientScopes";
import { useAlerts } from "../components/alert/Alerts";
import {
  ClientScope,
  allClientScopeTypes,
  changeClientScope,
  changeScope,
  clientScopeTypesSelectOptions,
} from "../components/client-scope/ClientScopeTypes";

type ChangeTypeDropdownProps = {
  clientId?: string;
  selectedRows: Row[];
  refresh: () => void;
};

export const ChangeTypeDropdown = ({
  clientId,
  selectedRows,
  refresh,
}: ChangeTypeDropdownProps) => {
  const { t } = useTranslation("client-scopes");
  const [open, setOpen] = useState(false);

  const { addAlert, addError } = useAlerts();

  return (
    <Select
      toggleId="change-type-dropdown"
      aria-label="change-type-to"
      isOpen={open}
      selections={[]}
      isDisabled={selectedRows.length === 0}
      placeholderText={t("changeTypeTo")}
      // eslint-disable-next-line @typescript-eslint/no-unused-vars
      onToggle={(_event) => setOpen}
      onSelect={async (_, value) => {
        try {
          await Promise.all(
            selectedRows.map((row) => {
              return clientId
                ? changeClientScope(
                    clientId,
                    row,
                    row.type,
                    value as ClientScope
                  )
                : changeScope(row, value as ClientScope);
            })
          );
          setOpen(false);
          refresh();
          addAlert(t("clientScopeSuccess"), AlertVariant.success);
        } catch (error) {
          addError("clients:clientScopeError", error);
        }
      }}
    >
      {clientScopeTypesSelectOptions(
        t,
        !clientId ? allClientScopeTypes : undefined
      )}
    </Select>
  );
};
