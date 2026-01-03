/**
 * Mock Data Index
 * Exports all mock data and utilities for AURA platform testing
 *
 * BYPASS LOGIN CREDENTIALS:
 * Email: test@aura.dev
 * Password: aura123
 */

export * from './mockUsers';
export * from './mockAura';
export * from './MockProvider';

import MockProvider from './MockProvider';
import { BYPASS_CREDENTIALS } from './mockUsers';

// Re-export for convenience
export { MockProvider, BYPASS_CREDENTIALS };

// Quick reference for test credentials
export const TEST_CREDENTIALS = {
  email: 'test@aura.dev',
  password: 'aura123',
  hint: 'Use these credentials to bypass auth and test AURA features',
};

export default {
  ...MockProvider,
  TEST_CREDENTIALS,
};
