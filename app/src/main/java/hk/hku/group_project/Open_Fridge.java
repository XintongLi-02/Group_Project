package hk.hku.group_project;

import static android.content.ContentValues.TAG;
import static java.util.Locale.filter;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hk.hku.group_project.adapters.FoodAdapter;
import hk.hku.group_project.database.DatabaseHelper;
import hk.hku.group_project.database.FirebaseCallback;
import hk.hku.group_project.database.FoodItem;
import hk.hku.group_project.utils.UserSession;

public class Open_Fridge extends AppCompatActivity {
    private RecyclerView foodRecyclerView;
    private FoodAdapter foodAdapter;
    private EditText txtSearch;
    private final List<FoodItem> foodList = new ArrayList<>(); // 主数据源（永不直接修改）
    private final List<FoodItem> displayList = new ArrayList<>();    // 显示用列表
    //private final List<FoodItem> filteredList = new ArrayList<>();

    private UserSession userSession;
    private String currentGroupId;
    //private String currentGroupId = "group123";

    private static final String TAG = "Open_Fridge";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_open_fridge);

        // 初始化RecyclerView
        foodRecyclerView = findViewById(R.id.foodRecyclerView);
        txtSearch = findViewById(R.id.txt_Search);
        foodRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        //
        userSession = new UserSession(getApplicationContext());
        currentGroupId = userSession.getGroupId();

        // 初始化视图
        initViews();

        // 设置搜索监听
        setupSearch();

        // 加载数据
        loadFoodData();

    }

    private void initViews() {
        Log.d(TAG,currentGroupId);
        foodRecyclerView = findViewById(R.id.foodRecyclerView);
        txtSearch = findViewById(R.id.txt_Search);

        // 设置RecyclerView
        foodRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        foodAdapter = new FoodAdapter(displayList, currentGroupId, this);
        foodRecyclerView.setAdapter(foodAdapter);
    }

    private void setupSearch() {
        txtSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadFoodData();         // 每次返回页面时刷新数据
    }

    private void filter(String text) {
        applyFilter(text); // 统一使用applyFilter处理
    }

    private boolean containsOwner(List<String> owners, String searchText) {
        if (owners == null) return false;
        for (String owner : owners) {
            if (owner != null && owner.toLowerCase().contains(searchText)) {
                return true;
            }
        }
        return false;
    }

    // 从数据库获取食物数据
    private void loadFoodData() {

        DatabaseHelper.getAllFoodsWithId(currentGroupId, new FirebaseCallback<Map<String, FoodItem>>() {
            @Override
            public void onSuccess(Map<String, FoodItem> foodMap) {
                Log.e("TestFood", "获取成功！");
                if (foodMap == null) {
                    Log.e("TestFood", "foodMap is null");
                    return;
                }

                foodList.clear();
                // 将Map转换为List，同时保留foodId
                if (foodMap != null) {
                    for (Map.Entry<String, FoodItem> entry : foodMap.entrySet()) {
                        FoodItem food = entry.getValue();
                        if (food != null) {
                            food.id = entry.getKey();
                            foodList.add(food);
                        }
                    }
                }

                //foodAdapter.notifyDataSetChanged();
                // 重置过滤列表（包含全部数据）
                applyFilter(txtSearch.getText().toString());
            }

            @Override
            public void onFailure(Exception e) {
                Log.e("TestFood", "加载失败", e);
                Toast.makeText(Open_Fridge.this, "加载食材失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyFilter(String query) {
        List<FoodItem> newDisplayList = new ArrayList<>();

        if (query.isEmpty()) {
            newDisplayList.addAll(new ArrayList<>(foodList)); // 防御性拷贝
        } else {
            String lowerQuery = query.toLowerCase();
            for (FoodItem item : foodList) {
                if (itemMatchesQuery(item, lowerQuery)) {
                    newDisplayList.add(item);
                }
            }
        }

        runOnUiThread(() -> {
            displayList.clear();
            displayList.addAll(newDisplayList);
            foodAdapter.updateList(new ArrayList<>(displayList)); // 再次拷贝确保隔离
            Log.d(TAG, "过滤完成. 主数据: " + foodList.size() +
                    ", 显示: " + displayList.size());

            // 添加调试日志
            if (displayList.isEmpty()) {
                Log.w(TAG, "显示列表为空！检查过滤条件");
                for (FoodItem item : foodList) {
                    Log.d(TAG, "主数据项: " + item.name);
                }
            }
        });
    }

    // 匹配方法
    private boolean itemMatchesQuery(FoodItem item, String query) {
        return item.name.toLowerCase().contains(query) ||
                (item.owners != null && containsOwner(item.owners, query)) ||
                item.storage.toLowerCase().contains(query) ||
                item.expiry.toLowerCase().contains(query);
    }

    public void add_food(View v) {
        Intent intent = new Intent(getBaseContext(), add_food.class);
        startActivity(intent);
    }
}
