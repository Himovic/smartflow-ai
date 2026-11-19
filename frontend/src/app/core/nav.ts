export interface NavItem {
  key: string;
  icon: string;
  route: string;
  module: string;
  action?: string;
}

export interface NavGroup {
  key: string;
  items: NavItem[];
}

/** Sidebar structure. Items are displayed only when the user's role and the tenant's enabled modules allow it. */
export const NAV: NavGroup[] = [
  { key: 'nav.group.overview', items: [
    { key: 'nav.executive', icon: 'space_dashboard', route: '/dashboard', module: 'dashboard' },
    { key: 'nav.qhseDashboard', icon: 'health_and_safety', route: '/dashboard/qhse', module: 'nc' },
    { key: 'nav.distributionDashboard', icon: 'local_shipping', route: '/dashboard/distribution', module: 'distribution' },
    { key: 'nav.analytics', icon: 'monitoring', route: '/analytics', module: 'analytics' },
  ] },
  { key: 'nav.group.operations', items: [
    { key: 'nav.orders', icon: 'receipt_long', route: '/orders', module: 'distribution' },
    { key: 'nav.deliveries', icon: 'conveyor_belt', route: '/deliveries', module: 'distribution' },
    { key: 'nav.routes', icon: 'route', route: '/route-planning', module: 'routes' },
    { key: 'nav.fleet', icon: 'airport_shuttle', route: '/fleet', module: 'fleet' },
    { key: 'nav.inventory', icon: 'inventory_2', route: '/inventory', module: 'inventory' },
    { key: 'nav.batches', icon: 'qr_code_2', route: '/batches', module: 'inventory' },
    { key: 'nav.traceability', icon: 'account_tree', route: '/traceability', module: 'traceability' },
    { key: 'nav.warehouses', icon: 'warehouse', route: '/warehouses', module: 'inventory' },
    { key: 'nav.products', icon: 'category', route: '/products', module: 'catalog' },
    { key: 'nav.customers', icon: 'storefront', route: '/customers', module: 'catalog' },
    { key: 'nav.suppliers', icon: 'handshake', route: '/suppliers', module: 'catalog' },
    { key: 'nav.equipment', icon: 'precision_manufacturing', route: '/equipment', module: 'assets' },
  ] },
  { key: 'nav.group.qhse', items: [
    { key: 'nav.nonConformities', icon: 'report', route: '/non-conformities', module: 'nc' },
    { key: 'nav.capa', icon: 'task_alt', route: '/capa', module: 'capa' },
    { key: 'nav.incidents', icon: 'emergency', route: '/incidents', module: 'incidents' },
    { key: 'nav.inspections', icon: 'fact_check', route: '/inspections', module: 'inspections' },
    { key: 'nav.checklists', icon: 'checklist', route: '/checklists', module: 'inspections' },
    { key: 'nav.risks', icon: 'grid_on', route: '/risk-assessments', module: 'risk' },
    { key: 'nav.permits', icon: 'badge', route: '/work-permits', module: 'safety' },
    { key: 'nav.audits', icon: 'policy', route: '/audits', module: 'audits' },
    { key: 'nav.documents', icon: 'folder_open', route: '/documents', module: 'documents' },
    { key: 'nav.training', icon: 'school', route: '/training', module: 'training' },
    { key: 'nav.hygiene', icon: 'sanitizer', route: '/hygiene', module: 'hygiene' },
    { key: 'nav.environment', icon: 'eco', route: '/environment', module: 'environment' },
  ] },
  { key: 'nav.group.intelligence', items: [
    { key: 'nav.aiCenter', icon: 'neurology', route: '/ai', module: 'ai' },
    { key: 'nav.assistant', icon: 'smart_toy', route: '/assistant', module: 'ai' },
    { key: 'nav.reports', icon: 'summarize', route: '/reports', module: 'reports' },
  ] },
  { key: 'nav.group.organization', items: [
    { key: 'nav.sites', icon: 'factory', route: '/sites', module: 'organization' },
    { key: 'nav.departments', icon: 'lan', route: '/departments', module: 'organization' },
    { key: 'nav.employees', icon: 'groups', route: '/employees', module: 'organization' },
  ] },
  { key: 'nav.group.administration', items: [
    { key: 'nav.integrations', icon: 'hub', route: '/integrations', module: 'integrations' },
    { key: 'nav.workflows', icon: 'account_tree', route: '/workflows', module: 'workflows' },
    { key: 'nav.users', icon: 'manage_accounts', route: '/users', module: 'users' },
    { key: 'nav.settings', icon: 'tune', route: '/settings', module: 'tenant' },
    { key: 'nav.demoAdmin', icon: 'science', route: '/demo-admin', module: 'demo' },
  ] },
];
