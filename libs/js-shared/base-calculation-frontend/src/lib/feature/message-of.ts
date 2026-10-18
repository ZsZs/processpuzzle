/** What to show for a failed request: the backend's `errorText` when it sent one, else what the failure says. */
export function messageOf(error: unknown): string {
  const body = (error as { error?: { errorText?: string } } | undefined)?.error;
  if (body?.errorText) return body.errorText;
  return error instanceof Error ? error.message : String(error);
}
