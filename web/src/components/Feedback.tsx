import type { ApiError } from '../api/client';

export function Loading({ label = 'Loading…' }: { label?: string }) {
  return (
    <p className="muted" role="status">
      {label}
    </p>
  );
}

export function ErrorBanner({ error, onRetry }: { error: ApiError | undefined; onRetry?: () => void }) {
  if (!error) {
    return null;
  }
  return (
    <div className="error" role="alert">
      <strong>{error.title}</strong> {error.message}
      {error.fieldErrors.length > 0 && (
        <ul>
          {error.fieldErrors.map((field) => (
            <li key={`${field.field}-${field.message}`}>
              {field.field}: {field.message}
            </li>
          ))}
        </ul>
      )}
      {onRetry && (
        <button type="button" className="link" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}
