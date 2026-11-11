package app.cuepipe;

import android.app.Application;

import org.schabi.newpipe.extractor.NewPipe;

public final class CuepipeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        NewPipe.init(new OkDownloader());
    }
}
