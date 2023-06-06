import { ComponentClass, MouseEventHandler, ReactNode } from "react";
import {
  EmptyState,
  EmptyStateIcon,
  EmptyStateBody,
  Button,
  ButtonVariant,
  EmptyStateActions,
  EmptyStateHeader,
  EmptyStateFooter,
} from "@patternfly/react-core";
import type { SVGIconProps } from "@patternfly/react-icons/dist/js/createIcon";
import { PlusCircleIcon, SearchIcon } from "@patternfly/react-icons";

export type Action = {
  text: string;
  type?: ButtonVariant;
  onClick: MouseEventHandler<HTMLButtonElement>;
};

export type ListEmptyStateProps = {
  message: string;
  instructions: ReactNode;
  primaryActionText?: string;
  onPrimaryAction?: MouseEventHandler<HTMLButtonElement>;
  hasIcon?: boolean;
  icon?: ComponentClass<SVGIconProps>;
  isSearchVariant?: boolean;
  secondaryActions?: Action[];
};

export const ListEmptyState = ({
  message,
  instructions,
  onPrimaryAction,
  hasIcon = true,
  isSearchVariant,
  primaryActionText,
  secondaryActions,
  icon,
}: ListEmptyStateProps) => {
  return (
    <EmptyState data-testid="empty-state" variant="lg">
      {hasIcon && isSearchVariant ? (
        <EmptyStateIcon icon={SearchIcon} />
      ) : (
        hasIcon && <EmptyStateIcon icon={icon ? icon : PlusCircleIcon} />
      )}
      {/* eslint-disable-next-line react/jsx-no-useless-fragment  */}
      <EmptyStateHeader titleText={<>{message}</>} headingLevel="h1" />
      <EmptyStateBody>{instructions}</EmptyStateBody>
      <EmptyStateFooter>
        {primaryActionText && (
          <Button
            data-testid={`${message
              .replace(/\W+/g, "-")
              .toLowerCase()}-empty-action`}
            variant="primary"
            onClick={onPrimaryAction}
          >
            {primaryActionText}
          </Button>
        )}
        {secondaryActions && (
          <EmptyStateActions>
            {secondaryActions.map((action) => (
              <Button
                key={action.text}
                data-testid={`${action.text
                  .replace(/\W+/g, "-")
                  .toLowerCase()}-empty-action`}
                variant={action.type || ButtonVariant.secondary}
                onClick={action.onClick}
              >
                {action.text}
              </Button>
            ))}
          </EmptyStateActions>
        )}
      </EmptyStateFooter>
    </EmptyState>
  );
};
