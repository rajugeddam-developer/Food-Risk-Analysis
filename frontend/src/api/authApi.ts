/**
 * API client for user authentication (Register & Login).
 */

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || 'http://localhost:8080';

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface UserProfile {
  id: string;
  name: string;
  email: string;
}

export interface RegisterResponse {
  message: string;
  user: UserProfile;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: UserProfile;
}

export interface ApiErrorResponse {
  message: string;
  errors?: Record<string, string>;
  timestamp?: string;
}

/**
 * Register a new user account.
 */
export async function registerUser(data: RegisterRequest): Promise<RegisterResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/register`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    body: JSON.stringify(data)
  });

  const responseData = await response.json().catch(() => ({}));

  if (!response.ok) {
    const errorMessage =
      responseData.message ||
      (responseData.errors ? Object.values(responseData.errors).join(', ') : 'Registration failed');
    throw new Error(errorMessage);
  }

  return responseData as RegisterResponse;
}

/**
 * Login user and retrieve JWT token.
 */
export async function loginUser(data: LoginRequest): Promise<AuthResponse> {
  const response = await fetch(`${API_BASE_URL}/api/auth/login`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json'
    },
    body: JSON.stringify(data)
  });

  const responseData = await response.json().catch(() => ({}));

  if (!response.ok) {
    const errorMessage = responseData.message || 'Invalid email or password.';
    throw new Error(errorMessage);
  }

  return responseData as AuthResponse;
}
