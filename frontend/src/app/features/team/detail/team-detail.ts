import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { AuthStore } from '../../../core/state/auth-store';
import { CompanyUsersStore } from '../../../core/state/company-users-store';
import { CompanyUserDetail } from '../../../core/types';
import { ConfirmDialogComponent } from '../../../shared/ui/confirm-dialog';
import { PasswordRevealModalComponent } from '../../../shared/ui/password-reveal-modal';
import { RoleChipComponent } from '../../../shared/ui/role-chip';
import { StatusBadgeComponent } from '../../../shared/ui/status-badge';

/**
 * Team detail page (`/team/:id`) — etapa-2-usuarios PR-5 T-5.6.
 *
 * <p>Read-only header + three tabs that surface the actions
 * a {@code COMPANY_ADMIN} can take on a single user:
 * - **Información** — name, email, timestamps, security
 *   columns (failed attempts, lock). "Editar información"
 *   navigates to `/team/:id/edit`.
 * - **Roles** — list of role chips with their descriptions.
 *   "Editar roles" is reserved for a future roles-management
 *   page (out of scope for PR-5).
 * - **Seguridad** — reset password, disable / reactivate the
 *   user. Each action confirms via a modal before firing the
 *   API call.
 *
 * <p>Protected-target rules (mirrors the rules on
 * `team-edit`):
 * - **First admin** of the tenant is read-only — info edit,
 *   role edit, disable all disabled. Their email + roles are
 *   immutable per PR-3 spec section B.4 + decision #8.
 * - **Self** — the signed-in user cannot edit their own info
 *   here (the future "My profile" surface handles self-edits).
 *   They can still reset their own password from this page
 *   (defensive — most apps would expose this only on "My
 *   profile" but the spec allows it here).
 *
 * <p>Standalone, OnPush, signal-first.
 */
@Component({
  selector: 'app-team-detail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    RouterLink,
    RoleChipComponent,
    StatusBadgeComponent,
    PasswordRevealModalComponent,
    ConfirmDialogComponent,
  ],
  templateUrl: './team-detail.html',
})
export class TeamDetailComponent implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly store = inject(CompanyUsersStore);
  protected readonly authStore = inject(AuthStore);

  /** Target user id resolved from the route param map. */
  protected readonly targetUserId = signal<string>('');

  /** Loaded detail. Mirrors the store signal so the template
   * has a single source of truth and the local component
   * methods can read fresh values without going back to the
   * store on every call. */
  protected readonly user = signal<CompanyUserDetail | null>(null);

  /** Local UI state. */
  protected readonly selectedTab = signal<'info' | 'roles' | 'security'>('info');
  protected readonly isLoading = signal<boolean>(false);
  protected readonly errorMessage = signal<string | null>(null);

  /** Modal triggers + the data the password reveal needs.
   * Exposed publicly for test access (mirrors `team-edit`'s
   * `form` public exposure pattern). */
  readonly showPasswordModal = signal<boolean>(false);
  readonly passwordModalData = signal<{
    temporaryPassword: string;
    warning: string;
    username: string;
  } | null>(null);
  readonly showDisableConfirm = signal<boolean>(false);
  readonly showReactivateConfirm = signal<boolean>(false);

  /** True when the target is the first admin of their tenant.
   * Server-derived — see PR-3 spec section B.4 + decision #8.
   * First-admin targets are read-only. */
  protected readonly isFirstAdmin = computed<boolean>(
    () => this.user()?.isFirstAdmin === true,
  );

  /** True when the target is the signed-in user. The spec
   * blocks self-edits from this page (the future "My profile"
   * surface handles self-edits). */
  protected readonly isSelf = computed<boolean>(() => {
    const me = this.authStore.currentUser();
    const targetId = this.user()?.id;
    if (!me || !targetId) return false;
    return me.id === targetId;
  });

  /** Tab button: disabled when the first-admin OR self
   * protections apply. */
  protected readonly canEditInfo = computed<boolean>(
    () => !this.isFirstAdmin() && !this.isSelf(),
  );
  protected readonly canEditRoles = computed<boolean>(
    () => !this.isFirstAdmin() && !this.isSelf(),
  );

  /** Security tab: disable / reactivate gated by the same
   * rules as info edit. Reset-password is allowed for self
   * (the spec leaves this open — admins may need to reset
   * their own password from the admin surface). */
  protected readonly canDisable = computed<boolean>(
    () =>
      !this.isFirstAdmin() &&
      !this.isSelf() &&
      this.user()?.status === 'ACTIVE',
  );
  protected readonly canReactivate = computed<boolean>(
    () =>
      !this.isFirstAdmin() &&
      !this.isSelf() &&
      this.user()?.status === 'DISABLED',
  );
  protected readonly canResetPassword = computed<boolean>(
    () => !this.isFirstAdmin(),
  );

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id') ?? '';
    this.targetUserId.set(id);
    if (!id) {
      void this.router.navigate(['/team']);
      return;
    }
    this.loadDetail();
  }

  /** Fetch the detail via the store and populate the local
   * user signal. */
  private loadDetail(): void {
    const id = this.targetUserId();
    if (!id) return;
    this.isLoading.set(true);
    this.errorMessage.set(null);
    void this.store
      .loadDetail(id)
      .then((detail) => {
        this.user.set(detail);
        this.isLoading.set(false);
      })
      .catch(() => {
        this.isLoading.set(false);
        this.errorMessage.set('No pudimos cargar el detalle. Probá de nuevo.');
      });
  }

  /** Navigate to the edit page when the "Editar información"
   * CTA is clicked. The template already disables the button
   * for protected targets; this is the handler. */
  protected editInfo(): void {
    const id = this.targetUserId();
    if (!id) return;
    void this.router.navigate(['/team', id, 'edit']);
  }

  /** Trigger the password reset flow. Opens the reveal modal
   * with the temporary password returned by the backend. */
  async resetPassword(): Promise<void> {
    const id = this.targetUserId();
    if (!id) return;
    try {
      const response = await this.store.resetPassword(id);
      this.passwordModalData.set({
        temporaryPassword: response.temporaryPassword,
        warning: response.passwordWarning,
        username: response.username,
      });
      this.showPasswordModal.set(true);
    } catch {
      this.errorMessage.set('No pudimos resetear la contraseña. Probá de nuevo.');
    }
  }

  /** Disable the user after the confirm dialog fires. */
  async disable(): Promise<void> {
    this.showDisableConfirm.set(false);
    const id = this.targetUserId();
    if (!id) return;
    try {
      await this.store.disable(id);
      // Refetch so the status badge flips to DISABLED and the
      // "Reactivar" button appears on the security tab.
      this.loadDetail();
    } catch {
      this.errorMessage.set('No pudimos desactivar al usuario. Probá de nuevo.');
    }
  }

  /** Reactivate the user after the confirm dialog fires. */
  async reactivate(): Promise<void> {
    this.showReactivateConfirm.set(false);
    const id = this.targetUserId();
    if (!id) return;
    try {
      await this.store.reactivate(id);
      this.loadDetail();
    } catch {
      this.errorMessage.set('No pudimos reactivar al usuario. Probá de nuevo.');
    }
  }

  /** Close the password reveal modal + clear its data. */
  onPasswordModalDismiss(): void {
    this.showPasswordModal.set(false);
    this.passwordModalData.set(null);
  }

  /** The "Crear otro" action is not exposed on this modal
   * (`showCreateAnother` defaults to false) — this handler
   * exists for API symmetry with the create flow and simply
   * closes the modal. */
  onPasswordModalCreateAnother(): void {
    this.showPasswordModal.set(false);
    this.passwordModalData.set(null);
  }

  /** Pure helper exposed to the template for the H1 fallback. */
  protected displayName(detail: CompanyUserDetail | null): string {
    if (!detail) return '';
    const f = (detail.firstName ?? '').trim();
    const l = (detail.lastName ?? '').trim();
    if (f && l) return `${f} ${l}`;
    if (f) return f;
    if (l) return l;
    return detail.username;
  }

  /** Pure helper: format an ISO-8601 timestamp as a short
   * Spanish date or "—" when null. */
  protected formatDate(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return d.toLocaleDateString('es-AR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    });
  }

  /** Pure helper: format an ISO-8601 timestamp as a short
   * Spanish date+time or "—" when null. */
  protected formatDateTime(iso: string | null): string {
    if (!iso) return '—';
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) return '—';
    return d.toLocaleString('es-AR', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }
}
