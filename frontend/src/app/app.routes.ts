import { Routes } from '@angular/router';
import { authGuard, permissionGuard } from './core/guards';
import { ShellComponent } from './layout/shell.component';
import { RESOURCES } from './features/resources/resources';

const list = (resource: string, path = resource) => ({
  path,
  loadComponent: () => import('./features/resources/resource-list.component').then((m) => m.ResourceListComponent),
  canActivate: [permissionGuard],
  data: { resource, module: RESOURCES[resource].module },
});

const tabbed = (path: string, module: string, title: string, icon: string, tabs: { resource: string; label: string; preset?: Record<string, string> }[]) => ({
  path,
  loadComponent: () => import('./features/misc/tabbed-resources.component').then((m) => m.TabbedResourcesComponent),
  canActivate: [permissionGuard],
  data: { module, title, icon, tabs },
});

const caseDetail = (resource: string) => ({
  path: `${resource}/:id`,
  loadComponent: () => import('./features/qhse/case-detail.component').then((m) => m.CaseDetailComponent),
  canActivate: [permissionGuard],
  data: { resource, module: RESOURCES[resource].module },
});

export const routes: Routes = [
  { path: 'demo', loadComponent: () => import('./features/public/demo-landing.component').then((m) => m.DemoLandingComponent) },
  { path: 'login', loadComponent: () => import('./features/public/login.component').then((m) => m.LoginComponent) },
  { path: 'qr/:type/:id', loadComponent: () => import('./features/public/public-qr.component').then((m) => m.PublicQrComponent) },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
      { path: 'dashboard', loadComponent: () => import('./features/dashboards/executive-dashboard.component').then((m) => m.ExecutiveDashboardComponent) },
      { path: 'dashboard/qhse', loadComponent: () => import('./features/dashboards/qhse-dashboard.component').then((m) => m.QhseDashboardComponent), canActivate: [permissionGuard], data: { module: 'nc' } },
      { path: 'dashboard/distribution', loadComponent: () => import('./features/dashboards/distribution-dashboard.component').then((m) => m.DistributionDashboardComponent), canActivate: [permissionGuard], data: { module: 'distribution' } },
      { path: 'analytics', loadComponent: () => import('./features/misc/analytics.component').then((m) => m.AnalyticsComponent), canActivate: [permissionGuard], data: { module: 'analytics' } },

      // Operations
      list('orders'),
      { path: 'orders/:id', loadComponent: () => import('./features/distribution/order-detail.component').then((m) => m.OrderDetailComponent), canActivate: [permissionGuard], data: { module: 'distribution' } },
      list('deliveries'),
      { path: 'deliveries/:id', loadComponent: () => import('./features/distribution/delivery-detail.component').then((m) => m.DeliveryDetailComponent), canActivate: [permissionGuard], data: { module: 'distribution' } },
      { path: 'route-planning', loadComponent: () => import('./features/distribution/route-planning.component').then((m) => m.RoutePlanningComponent), canActivate: [permissionGuard], data: { module: 'routes' } },
      tabbed('fleet', 'fleet', 'nav.fleet', 'airport_shuttle', [{ resource: 'vehicles', label: 'res.vehicles' }, { resource: 'drivers', label: 'res.drivers' },
        { resource: 'fuel-logs', label: 'res.fuel-logs' }, { resource: 'inspections', label: 'fleet.hygieneChecks', preset: { inspectionType: 'VEHICLE' } }]),
      tabbed('inventory', 'inventory', 'nav.inventory', 'inventory_2', [{ resource: 'stock-levels', label: 'res.stock-levels' }, { resource: 'stock-movements', label: 'res.stock-movements' }]),
      list('batches'),
      { path: 'traceability', loadComponent: () => import('./features/distribution/traceability.component').then((m) => m.TraceabilityComponent), canActivate: [permissionGuard], data: { module: 'traceability' } },
      { path: 'traceability/:id', loadComponent: () => import('./features/distribution/traceability.component').then((m) => m.TraceabilityComponent), canActivate: [permissionGuard], data: { module: 'traceability' } },
      list('warehouses'), list('products'), list('customers'), list('suppliers'),
      tabbed('equipment', 'assets', 'nav.equipment', 'precision_manufacturing', [{ resource: 'equipment', label: 'res.equipment' }, { resource: 'maintenance-work-orders', label: 'res.maintenance-work-orders' }]),

      // QHSE
      list('non-conformities'), caseDetail('non-conformities'),
      list('capa'), caseDetail('capa'),
      list('incidents'), caseDetail('incidents'),
      list('inspections'),
      { path: 'inspections/:id', loadComponent: () => import('./features/qhse/inspection-run.component').then((m) => m.InspectionRunComponent), canActivate: [permissionGuard], data: { module: 'inspections' } },
      list('inspection-templates', 'checklists'),
      { path: 'checklists/:id', loadComponent: () => import('./features/qhse/checklist-editor.component').then((m) => m.ChecklistEditorComponent), canActivate: [permissionGuard], data: { module: 'inspections' } },
      { path: 'risk-assessments', loadComponent: () => import('./features/qhse/risk-matrix.component').then((m) => m.RiskMatrixComponent), canActivate: [permissionGuard], data: { module: 'risk' } },
      tabbed('work-permits', 'safety', 'nav.permits', 'badge', [{ resource: 'work-permits', label: 'res.work-permits' }, { resource: 'ppe-items', label: 'res.ppe-items' },
        { resource: 'ppe-issuances', label: 'res.ppe-issuances' }]),
      list('audits'),
      { path: 'audits/:id', loadComponent: () => import('./features/qhse/audit-detail.component').then((m) => m.AuditDetailComponent), canActivate: [permissionGuard], data: { module: 'audits' } },
      list('documents'), caseDetail('documents'),
      { path: 'training', loadComponent: () => import('./features/misc/training.component').then((m) => m.TrainingComponent), canActivate: [permissionGuard], data: { module: 'training' } },
      tabbed('hygiene', 'hygiene', 'nav.hygiene', 'sanitizer', [{ resource: 'inspections', label: 'hygiene.inspections', preset: { domain: 'HYGIENE' } },
        { resource: 'cleaning-schedules', label: 'res.cleaning-schedules' }, { resource: 'cleaning-records', label: 'res.cleaning-records' },
        { resource: 'pest-control-visits', label: 'res.pest-control-visits' }, { resource: 'environmental-readings', label: 'hygiene.temperature', preset: { readingType: 'TEMPERATURE' } },
        { resource: 'incidents', label: 'hygiene.sanitaryIncidents', preset: { domain: 'HYGIENE' } }]),
      { path: 'environment', loadComponent: () => import('./features/misc/environment.component').then((m) => m.EnvironmentComponent), canActivate: [permissionGuard], data: { module: 'environment' } },

      // Intelligence
      { path: 'ai', loadComponent: () => import('./features/ai/ai-center.component').then((m) => m.AiCenterComponent), canActivate: [permissionGuard], data: { module: 'ai' } },
      { path: 'assistant', loadComponent: () => import('./features/ai/assistant.component').then((m) => m.AssistantComponent), canActivate: [permissionGuard], data: { module: 'ai' } },
      { path: 'reports', loadComponent: () => import('./features/misc/reports.component').then((m) => m.ReportsComponent), canActivate: [permissionGuard], data: { module: 'reports' } },

      // Organization
      list('sites'), list('departments'), list('employees'),
      list('production-lines'),

      // Administration
      { path: 'integrations', loadComponent: () => import('./features/admin/integration-hub.component').then((m) => m.IntegrationHubComponent), canActivate: [permissionGuard], data: { module: 'integrations' } },
      { path: 'workflows', loadComponent: () => import('./features/admin/workflows.component').then((m) => m.WorkflowsComponent), canActivate: [permissionGuard], data: { module: 'workflows' } },
      { path: 'users', loadComponent: () => import('./features/admin/users.component').then((m) => m.UsersComponent), canActivate: [permissionGuard], data: { module: 'users' } },
      { path: 'settings', loadComponent: () => import('./features/admin/settings.component').then((m) => m.SettingsComponent), canActivate: [permissionGuard], data: { module: 'tenant' } },
      { path: 'demo-admin', loadComponent: () => import('./features/admin/demo-admin.component').then((m) => m.DemoAdminComponent), canActivate: [permissionGuard], data: { module: 'demo' } },
      { path: 'notifications', loadComponent: () => import('./features/admin/notifications.component').then((m) => m.NotificationsComponent) },
      { path: 'profile', loadComponent: () => import('./features/misc/profile.component').then((m) => m.ProfileComponent) },
      { path: 'forbidden', loadComponent: () => import('./features/misc/forbidden.component').then((m) => m.ForbiddenComponent) },
    ],
  },
  { path: '**', redirectTo: '' },
];
