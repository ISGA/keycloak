import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generateEncodedPath } from "../../util";
import type { AppRouteObject } from "../../routes";

export type NewRoleParams = { realm: string; clientId: string };

const CreateClientRole = lazy(() => import("../roles/CreateClientRole"));

export const NewRoleRoute: AppRouteObject = {
  path: "/:realm/clients/:clientId/roles/new",
  element: <CreateClientRole />,
  breadcrumb: (t) => t("createRole"),
  handle: {
    access: "manage-clients",
  },
};

export const toCreateRole = (params: NewRoleParams): Partial<Path> => ({
  pathname: generateEncodedPath(NewRoleRoute.path, params),
});
