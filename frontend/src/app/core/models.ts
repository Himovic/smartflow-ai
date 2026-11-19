export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Me {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  jobTitle?: string;
  role: string;
  language: string;
  allSites: boolean;
  siteIds: string[];
  permissions: Record<string, string[]>;
  homeTenantId: string;
  demo: boolean;
  tenant: TenantView;
}

export interface TenantView {
  id: string;
  code: string;
  name: string;
  industryProfile: string;
  demo: boolean;
  defaultLanguage?: string;
  settings: {
    enabledModules: string[];
    terminology: Record<string, string>;
    riskCategories: string[];
    hazardCategories: string[];
    ncTypes: string[];
    incidentTypes: string[];
    referenceStandards: string[];
    emissionFactors: Record<string, number>;
    currency: string;
    publicQrAccess: boolean;
    llm: { provider: string; baseUrl?: string; model?: string; apiKeyConfigured: boolean };
    [k: string]: unknown;
  };
}

export interface Kpi {
  code: string;
  label: string;
  value: number;
  unit: string;
  previous?: number | null;
  target?: number;
  direction?: string;
  onTarget?: boolean;
  nature: string;
}

export interface LookupItem {
  id: string;
  label: string;
}

export interface Alert {
  severity: string;
  category: string;
  module: string;
  title: string;
  detail: string;
  entityType?: string;
  entityId?: string;
  route?: string;
}
