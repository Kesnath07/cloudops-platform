import type { Page } from '../api/types';

export function Pager({ page, onChange }: { page: Page<unknown>; onChange: (page: number) => void }) {
  if (page.totalPages <= 1) {
    return null;
  }
  return (
    <div className="pager">
      <button type="button" disabled={page.page === 0} onClick={() => onChange(page.page - 1)}>
        Previous
      </button>
      <span className="muted">
        Page {page.page + 1} of {page.totalPages}
      </span>
      <button type="button" disabled={page.page + 1 >= page.totalPages} onClick={() => onChange(page.page + 1)}>
        Next
      </button>
    </div>
  );
}
