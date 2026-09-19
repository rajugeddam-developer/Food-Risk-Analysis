import React from 'react';
import { useAuth } from '../context/AuthContext';
import { PublicHome } from './PublicHome';
import { AuthenticatedHome } from './AuthenticatedHome';

export const HomePage: React.FC = () => {
  const { isAuthenticated, user } = useAuth();

  if (isAuthenticated) {
    return <AuthenticatedHome user={user} />;
  }

  return <PublicHome />;
};

export default HomePage;
