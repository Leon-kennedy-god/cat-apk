package com.meow.assistant;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

public class RewriteActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String msg = ClipboardRewriter.rewrite(this);
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        finish();
    }
}