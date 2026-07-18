import { connectAuthEmulator, getAuth } from '@angular/fire/auth';
import { FirebaseApp } from '@angular/fire/app';
import { inject } from '@angular/core';
import { FirebaseAuthService } from './firebase-auth.service';
import { BaseConfiguration } from '@processpuzzle/util';
import { AuthenticationConfiguration } from './provide-authentication.service';

export function provideFirebaseAuthService(baseConfig: BaseConfiguration, authConfig: AuthenticationConfiguration): FirebaseAuthService {
  // Inject FirebaseApp to force AngularFire's lazy initializeApp() to run before getAuth().
  // The bootstrap auth initializer resolves AUTHENTICATION_SERVICE before any component injects
  // Firebase, so without this getAuth() throws "No Firebase App '[DEFAULT]'".
  const auth = getAuth(inject(FirebaseApp));
  const pipelineStage = baseConfig.PIPELINE_STAGE ?? 'ci';
  if ((pipelineStage === 'dev' || pipelineStage === 'ci') && authConfig.AUTHENTICATION_SERVICE_ROOT) {
    connectAuthEmulator(auth, authConfig.AUTHENTICATION_SERVICE_ROOT);
  }
  return new FirebaseAuthService(auth);
}
