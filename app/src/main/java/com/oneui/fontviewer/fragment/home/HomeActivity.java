package com.oneui.fontviewer.fragment.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import com.oneui.fontviewer.R;
import com.oneui.fontviewer.activity.BaseActivity;

public class HomeActivity extends BaseActivity {

    private HomeFragment mHomeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        if (savedInstanceState == null) {
            mHomeFragment = new HomeFragment();
            getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.home_container, mHomeFragment)
                .commit();
        } else {
            mHomeFragment = (HomeFragment) getSupportFragmentManager()
                .findFragmentById(R.id.home_container);
        }
    }

    @Override
    public void onBackPressed() {
        if (mHomeFragment != null && mHomeFragment.handleBackPressed()) {
            return;
        }
        
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.O && isTaskRoot()) {
            finishAfterTransition();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_app_info) {
            Intent intent = new Intent(
                    "android.settings.APPLICATION_DETAILS_SETTINGS",
                    Uri.fromParts("package", getPackageName(), null)
            );
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
