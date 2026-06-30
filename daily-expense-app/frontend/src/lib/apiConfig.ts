// Empty string = relative URLs; Vite dev proxy routes each /api/v1/* prefix to the correct backend port
export const API_BASE_URL: string = import.meta.env.VITE_API_BASE_URL ?? ''
