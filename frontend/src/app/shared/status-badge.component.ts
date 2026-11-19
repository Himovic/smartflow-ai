import { Component, computed, input } from '@angular/core';
import { EnumPipe } from './tr.pipe';

const COLORS: Record<string, string> = {
  CRITICAL: 'red', HIGH: 'red', MAJOR: 'amber', MEDIUM: 'amber', MINOR: 'blue', LOW: 'green',
  CLOSED: 'green', COMPLETED: 'green', DELIVERED: 'green', APPROVED: 'green', RELEASED: 'green', PASS: 'green', SUCCESS: 'green', VALID: 'green',
  OPERATIONAL: 'green', AVAILABLE: 'green', ACTIVE: 'teal', QUALIFIED: 'green', DONE: 'green', EFFECTIVE: 'green', RUNNING: 'teal',
  REPORTED: 'blue', OPEN: 'blue', PLANNED: 'blue', SCHEDULED: 'blue', DRAFT: '', CONFIRMED: 'blue', PENDING: 'blue', REQUESTED: 'blue',
  ASSIGNED: 'violet', ROOT_CAUSE_ANALYSIS: 'violet', UNDER_INVESTIGATION: 'violet', IMPACT_ASSESSMENT: 'violet', IN_REVIEW: 'amber',
  ACTION_PLAN: 'amber', IN_PROGRESS: 'amber', ACTIONS_IN_PROGRESS: 'amber', IN_TRANSIT: 'teal', ON_ROUTE: 'teal', SHIPPED: 'teal', PREPARING: 'amber',
  PENDING_VERIFICATION: 'teal', CONDITIONAL: 'amber', PARTIAL: 'amber', EXPIRING: 'amber', MAINTENANCE: 'amber', QUARANTINE: 'amber', ON_DUTY: 'teal',
  FAIL: 'red', FAILED: 'red', DOWN: 'red', BLOCKED: 'red', RECALLED: 'red', EXPIRED: 'red', SUSPENDED: 'red', REJECTED: 'red', DELAYED: 'red',
  CONFLICT: 'red', MISSING: 'red', ACTION_REQUIRED: 'red', OUT_OF_SERVICE: 'red', CANCELLED: '', OBSOLETE: '', ARCHIVED: '',
  SIMULATED: 'amber', LIVE: 'green', MEASURED: 'green', PREDICTION: 'violet', RISK_INDEX: 'amber',
};

@Component({
  selector: 'sf-badge',
  imports: [EnumPipe],
  template: `@if (value()) {<span class="badge" [class]="'badge ' + color()">{{ value() | enum }}</span>} @else {<span class="muted">—</span>}`,
})
export class StatusBadgeComponent {
  readonly value = input<string | null | undefined>();
  readonly color = computed(() => COLORS[this.value() ?? ''] ?? '');
}
