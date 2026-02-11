import { connectAuthEmulator, getAuth } from '@angular/fire/auth';
import { FirebaseAuthService } from './firebase-auth.service';
import { BaseConfiguration } from '@processpuzzle/util';
import { AuthenticationConfiguration } from './provide-authentication.service';

export function provideFirebaseAuthService(baseConfig: BaseConfiguration, authConfig: AuthenticationConfiguration): FirebaseAuthService {
  const auth = getAuth();
  const pipelineStage = baseConfig.PIPELINE_STAGE ?? 'ci';
  if ((pipelineStage === 'dev' || pipelineStage === 'ci') && authConfig.AUTHENTICATION_SERVICE_ROOT) {
    connectAuthEmulator(auth, authConfig.AUTHENTICATION_SERVICE_ROOT);
  }
  return new FirebaseAuthService(auth);
}
