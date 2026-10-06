import { describe, expect, it } from 'vitest';
import { formatDateTime, humanize, optionalField, requiredField } from './format';
import { nextStatuses } from './incidents';

function form(entries: Record<string, string>): FormData {
  const data = new FormData();
  for (const [name, value] of Object.entries(entries)) {
    data.set(name, value);
  }
  return data;
}

describe('format helpers', () => {
  it('humanizes enum constants', () => {
    expect(humanize('PARTIAL_OUTAGE')).toBe('Partial outage');
    expect(humanize('SEV1')).toBe('Sev1');
    expect(humanize('')).toBe('');
  });

  it('formats missing timestamps as a dash', () => {
    expect(formatDateTime(null)).toBe('—');
    expect(formatDateTime(undefined)).toBe('—');
    expect(formatDateTime('2026-03-01T10:00:00Z')).not.toBe('—');
  });

  it('trims optional fields and maps blank input to undefined', () => {
    const data = form({ name: '  Payments  ', description: '   ' });

    expect(optionalField(data, 'name')).toBe('Payments');
    expect(optionalField(data, 'description')).toBeUndefined();
    expect(optionalField(data, 'missing')).toBeUndefined();
  });

  it('reads required fields as trimmed strings, never undefined', () => {
    const data = form({ slug: ' payments ', blank: ' ' });

    expect(requiredField(data, 'slug')).toBe('payments');
    expect(requiredField(data, 'blank')).toBe('');
    expect(requiredField(data, 'missing')).toBe('');
  });
});

describe('incident lifecycle', () => {
  it('mirrors the API transitions', () => {
    expect(nextStatuses('OPEN')).toEqual(['MITIGATED', 'RESOLVED']);
    expect(nextStatuses('MITIGATED')).toEqual(['OPEN', 'RESOLVED']);
    expect(nextStatuses('RESOLVED')).toEqual([]);
  });
});
