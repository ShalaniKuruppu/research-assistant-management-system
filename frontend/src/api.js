const TOKEN_KEY = 'rams.token'

export function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token)
  } else {
    localStorage.removeItem(TOKEN_KEY)
  }
}

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  const token = getToken()
  if (token && !path.startsWith('/api/auth/')) {
    headers.Authorization = `Bearer ${token}`
  }

  const response = await fetch(path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  })

  return readJson(response)
}

export async function apiFormData(path, formData, { method = 'POST' } = {}) {
  const headers = { Accept: 'application/json' }
  const token = getToken()
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }
  const response = await fetch(path, {
    method,
    headers,
    body: formData,
  })
  return readJson(response)
}

async function readJson(response) {
  const text = await response.text()
  let data = null
  try {
    data = text ? JSON.parse(text) : null
  } catch {
    throw new ApiError(response.status, 'The server did not return JSON')
  }
  if (!response.ok) {
    const fieldMessage = data?.fields ? Object.values(data.fields).filter(Boolean).join(' ') : ''
    throw new ApiError(response.status, fieldMessage || data?.message || 'The request failed')
  }
  return data
}
