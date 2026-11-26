package app.xavine.car;

import androidx.annotation.NonNull;
import androidx.car.app.CarAppService;
import androidx.car.app.Session;
import androidx.car.app.validation.HostValidator;

/** Entry point Android Auto binds to. */
public final class XavineCarAppService extends CarAppService {

    @NonNull
    @Override
    public HostValidator createHostValidator() {
        // Personal sideloaded app: accept the Android Auto host on this phone.
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR;
    }

    @NonNull
    @Override
    public Session onCreateSession() {
        return new XavineSession();
    }
}
