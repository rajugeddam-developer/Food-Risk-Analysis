import React, { createContext, useContext, useState, useEffect, type ReactNode } from 'react';
import { UserProfile, AuthResponse } from '../api/authApi';
import {
  getStoredToken,
  setStoredToken,
  removeStoredToken,
  getStoredUser,
  setStoredUser,
  removeStoredUser,
  getCurrentUser
} from '../api/userApi';

interface AuthContextType {
  isAuthenticated: boolean;
  user: UserProfile | null;
  token: string | null;
  isLoading: boolean;
  login: (authData: AuthResponse) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(() => getStoredToken());
  const [user, setUser] = useState<UserProfile | null>(() => getStoredUser());
  const [isLoading, setIsLoading] = useState<boolean>(true);

  useEffect(() => {
    const existingToken = getStoredToken();
    if (existingToken) {
      // Verify token freshness against /api/user/me
      getCurrentUser()
        .then(profile => {
          setUser(profile);
          setStoredUser(profile);
        })
        .catch(() => {
          // Token expired or invalid
          logout();
        })
        .finally(() => {
          setIsLoading(false);
        });
    } else {
      setIsLoading(false);
    }
  }, []);

  const login = (authData: AuthResponse) => {
    setToken(authData.accessToken);
    setUser(authData.user);
    setStoredToken(authData.accessToken);
    setStoredUser(authData.user);
  };

  const logout = () => {
    setToken(null);
    setUser(null);
    removeStoredToken();
    removeStoredUser();
  };

  const value: AuthContextType = {
    isAuthenticated: !!token && !!user,
    user,
    token,
    isLoading,
    login,
    logout
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = (): AuthContextType => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
