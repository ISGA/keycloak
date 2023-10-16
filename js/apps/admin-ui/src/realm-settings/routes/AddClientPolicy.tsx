import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generateEncodedPath } from "../../util";
import type { AppRouteObject } from "../../routes";

export type AddClientPolicyParams = { realm: string };

const NewClientPolicyForm = lazy(() => import("../NewClientPolicyForm"));

export const AddClientPolicyRoute: AppRouteObject = {
  path: "/:realm/realm-settings/client-policies/policies/add-client-policy",
  element: <NewClientPolicyForm />,
  breadcrumb: (t) => t("createPolicy"),
  handle: {
    access: "manage-clients",
  },
};

export const toAddClientPolicy = (
  params: AddClientPolicyParams,
): Partial<Path> => ({
  pathname: generateEncodedPath(AddClientPolicyRoute.path, params),
});
