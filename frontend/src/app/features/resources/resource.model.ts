export type FieldType = 'text' | 'textarea' | 'number' | 'date' | 'datetime' | 'select' | 'lookup' | 'boolean' | 'tags' | 'time';
export type ColType = 'text' | 'badge' | 'date' | 'datetime' | 'number' | 'lookup' | 'boolean' | 'money' | 'percent' | 'score' | 'strong';

export interface FieldDef {
  key: string;
  type: FieldType;
  options?: string[];
  lookup?: string;
  required?: boolean;
  full?: boolean;
  step?: number;
}

export interface ColumnDef {
  key: string;
  type?: ColType;
  lookup?: string;
}

export interface FilterDef {
  key: string;
  options?: string[];
  lookup?: string;
}

export interface ResourceDef {
  key: string;
  endpoint: string;
  module: string;
  icon: string;
  columns: ColumnDef[];
  fields: FieldDef[];
  filters?: FilterDef[];
  sort?: string;
  detail?: string;
  entityType?: string;
  qr?: string;
  readOnly?: boolean;
  noDelete?: boolean;
  defaults?: Record<string, unknown>;
}

// --- concise builders
export const f = {
  text: (key: string, required = false, full = false): FieldDef => ({ key, type: 'text', required, full }),
  area: (key: string, required = false): FieldDef => ({ key, type: 'textarea', required, full: true }),
  num: (key: string, required = false, step = 1): FieldDef => ({ key, type: 'number', required, step }),
  date: (key: string, required = false): FieldDef => ({ key, type: 'date', required }),
  dt: (key: string, required = false): FieldDef => ({ key, type: 'datetime', required }),
  time: (key: string): FieldDef => ({ key, type: 'time' }),
  sel: (key: string, options: string[], required = false): FieldDef => ({ key, type: 'select', options, required }),
  lk: (key: string, lookup: string, required = false): FieldDef => ({ key, type: 'lookup', lookup, required }),
  bool: (key: string): FieldDef => ({ key, type: 'boolean' }),
  tags: (key: string): FieldDef => ({ key, type: 'tags', full: true }),
};
export const c = {
  text: (key: string): ColumnDef => ({ key }),
  strong: (key: string): ColumnDef => ({ key, type: 'strong' }),
  badge: (key: string): ColumnDef => ({ key, type: 'badge' }),
  date: (key: string): ColumnDef => ({ key, type: 'date' }),
  dt: (key: string): ColumnDef => ({ key, type: 'datetime' }),
  num: (key: string): ColumnDef => ({ key, type: 'number' }),
  money: (key: string): ColumnDef => ({ key, type: 'money' }),
  pct: (key: string): ColumnDef => ({ key, type: 'percent' }),
  score: (key: string): ColumnDef => ({ key, type: 'score' }),
  bool: (key: string): ColumnDef => ({ key, type: 'boolean' }),
  lk: (key: string, lookup: string): ColumnDef => ({ key, type: 'lookup', lookup }),
};
