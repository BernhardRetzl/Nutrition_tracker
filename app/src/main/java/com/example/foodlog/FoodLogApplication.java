package com.example.foodlog;

import android.app.Application;

public class FoodLogApplication extends Application {
    private SendController sender;
    private AutoSync sync;
    AutoSync sync(){if(sync==null)sync=new AutoSync(this);return sync;}
    SendController sender() {
        if(sender==null)sender=new SendController(this);
        return sender;
    }
}
