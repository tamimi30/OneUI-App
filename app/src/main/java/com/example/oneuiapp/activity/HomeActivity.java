package com.example.oneuiapp.activity;

import android.os.Build;
import android.os.Bundle;

import com.example.oneuiapp.R;
import com.example.oneuiapp.fragment.HomeFragment;

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
}
