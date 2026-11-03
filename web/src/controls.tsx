import { Button, Tooltip } from '@heroui/react';
import type { ComponentProps, ReactNode } from 'react';
import { Icon, type IconName } from './icons';

type IconButtonProps = { name: string; icon: IconName; reason?: string; disabled?: boolean; onPress?: () => void };
export function IconButton({ name, icon, reason = '', disabled = false, onPress }: IconButtonProps) {
  return <Tooltip delay={350} closeDelay={0}>
    <Button aria-label={name} aria-disabled={disabled} className="icon-button" variant="ghost" isIconOnly onPress={() => { if (!disabled) onPress?.(); }}>
      <Icon name={icon} />
    </Button>
    <Tooltip.Content className="help-tooltip">{disabled && reason ? `${name} · ${reason}` : name}</Tooltip.Content>
  </Tooltip>;
}
export function ActionButton({ children, ...props }: ComponentProps<typeof Button> & { children: ReactNode }) {
  return <Button {...props} className={`action-button ${typeof props.className === 'string' ? props.className : ''}`}>{children}</Button>;
}
