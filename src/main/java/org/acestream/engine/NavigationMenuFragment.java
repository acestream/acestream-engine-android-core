package org.acestream.engine;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

public class NavigationMenuFragment extends Fragment {

    private static final String TAG = "AceStream/NavMenu";
    private ListView mDrawerList;
    private DrawerLayout mDrawerLayout;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_navigation_menu, container, false);
        mDrawerList = (ListView) view.findViewById(R.id.lvMainMenu);
        buildNavigationMenu();

        return view;
    }


    @Override
    public void onResume() {
        super.onResume();
    }

    public void setDrawerLayout(DrawerLayout drawerLayout) {
        mDrawerLayout = drawerLayout;
    }

    private void closeDrawer() {
        if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
            mDrawerLayout.closeDrawer(GravityCompat.START);
        }
    }

    private void buildNavigationMenu() {

        final List<MenuEntity> menu = new ArrayList<>();

        MenuEntity homeMenuItem = new MenuEntity();
        homeMenuItem.CollectionType = "root";
        homeMenuItem.Name = "Home";
        menu.add(homeMenuItem);

        MenuEntity remoteControlMenuItem = new MenuEntity();
        remoteControlMenuItem.CollectionType = "remote_control";
        remoteControlMenuItem.Name = "Remote Control";
        menu.add(remoteControlMenuItem);

        mDrawerList.setAdapter(new ViewsAdapter(menu));

        mDrawerList.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View v, int index, long id) {

                if (mDrawerLayout != null && mDrawerLayout.isDrawerOpen(GravityCompat.START)) {
                    mDrawerLayout.closeDrawer(GravityCompat.END);
                }

                if (index == 0) {
                    Intent intent = new Intent(AceStreamEngineBaseApplication.context(), AceStreamEngineBaseApplication.getMainActivityClass());
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    Activity activity = getActivity();
                    if (activity != null) {
                        activity.finish();
                    }
                } else if (menu.get(index).CollectionType != null && menu.get(index).CollectionType.equalsIgnoreCase("remote_control")) {
                    Intent intent = new Intent(getActivity(), RemoteControlActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                    startActivity(intent);
                }
            }
        });

    }
}
