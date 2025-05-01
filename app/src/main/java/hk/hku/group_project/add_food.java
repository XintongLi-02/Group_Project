//package hk.hku.group_project;
//
//import android.os.Bundle;
//
//import androidx.activity.EdgeToEdge;
//import androidx.appcompat.app.AppCompatActivity;
//import androidx.core.graphics.Insets;
//import androidx.core.view.ViewCompat;
//import androidx.core.view.WindowInsetsCompat;
//
//public class add_food extends AppCompatActivity {
//
//    @Override
//    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_add_food);
//
//
//    }
//}

package hk.hku.group_project;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.FirebaseApp;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import hk.hku.group_project.database.DatabaseHelper;
import hk.hku.group_project.database.FirebaseCallback;
import hk.hku.group_project.database.FoodItem;
import hk.hku.group_project.utils.UserSession;

public class add_food extends AppCompatActivity {

    Button btnTestAdd;
    private EditText etFoodName, etExpiry, etPrice;
    private AutoCompleteTextView actvOwners;
    private RadioGroup rgStorage;
    private Button btnSubmit;
    private UserSession userSession;
    private String currentGroupId;
    private List<String> groupMembers = new ArrayList<>();
    private final Calendar calendar = Calendar.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化 Firebase
        FirebaseApp.initializeApp(this);
        FirebaseDatabase.getInstance(); // 可选，不用强制赋值
        setContentView(R.layout.activity_add_food);

        //
        userSession = new UserSession(getApplicationContext());
        currentGroupId = userSession.getGroupId();

        // 初始化视图
        initViews();

        // 加载群组成员
        loadGroupMembers();

        // 设置保质期选择
        setupExpiryPicker();

        // 设置提交按钮
        setupSubmitButton();

        //btnTestAdd = findViewById(R.id.btn_test_add);

    }

    private void initViews() {
        etFoodName = findViewById(R.id.etFoodName);
        etExpiry = findViewById(R.id.etExpiry);
        etPrice = findViewById(R.id.etPrice);
        actvOwners = findViewById(R.id.actvOwners);
        rgStorage = findViewById(R.id.rgStorage);
        btnSubmit = findViewById(R.id.btn_test_add);
    }

    private void loadGroupMembers() {
        DatabaseHelper.getGroupMembers(currentGroupId, new FirebaseCallback<List<String>>() {
            @Override
            public void onSuccess(List<String> members) {
                groupMembers = members;
                setupOwnersDropdown();
            }

            @Override
            public void onFailure(Exception e) {
                Toast.makeText(add_food.this, "加载成员失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupOwnersDropdown() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_dropdown_item_1line,
                groupMembers
        );
        actvOwners.setAdapter(adapter);
        actvOwners.setThreshold(1); // 输入1个字符后显示建议

        // 多选功能
        actvOwners.setOnClickListener(v -> showMultiSelectDialog());
    }

    private void showMultiSelectDialog() {
        if (groupMembers.isEmpty()) {
            Toast.makeText(this, "暂无群组成员", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean[] selectedItems = new boolean[groupMembers.size()];
        new MaterialAlertDialogBuilder(this)
                .setTitle("select owners of the food(in your group)")
                .setMultiChoiceItems(
                        groupMembers.toArray(new String[0]),
                        selectedItems,
                        (dialog, which, isChecked) -> {}
                )
                .setPositiveButton("OK", (dialog, which) -> {
                    StringBuilder selected = new StringBuilder();
                    for (int i = 0; i < selectedItems.length; i++) {
                        if (selectedItems[i]) {
                            if (selected.length() > 0) selected.append(", ");
                            selected.append(groupMembers.get(i));
                        }
                    }
                    actvOwners.setText(selected.toString());
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void setupExpiryPicker() {
        etExpiry.setOnClickListener(v -> {
            DatePickerDialog datePicker = new DatePickerDialog(
                    this,
                    (view, year, month, dayOfMonth) -> {
                        calendar.set(year, month, dayOfMonth);
                        // 检查是否早于今天
                        if (calendar.getTime().before(new Date())) {
                            Toast.makeText(this, "保质期不能早于今天", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        // 格式化日期
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                        etExpiry.setText(sdf.format(calendar.getTime()));
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
            );
            datePicker.getDatePicker().setMinDate(System.currentTimeMillis());
            datePicker.show();
        });
    }

    private void setupSubmitButton() {
        btnSubmit.setOnClickListener(v -> {
            // 验证输入
            String name = etFoodName.getText().toString().trim();
            String ownersText = actvOwners.getText().toString().trim();
            String expiry = etExpiry.getText().toString().trim();
            String priceText = etPrice.getText().toString().trim();
            int selectedId = rgStorage.getCheckedRadioButtonId();

            if (name.isEmpty() || ownersText.isEmpty() || expiry.isEmpty() || priceText.isEmpty() || selectedId == -1) {
                Toast.makeText(this, "Please fill in all the blank!", Toast.LENGTH_SHORT).show();
                return;
            }

            // 处理数据
            List<String> owners = Arrays.asList(ownersText.split(",\\s*"));
            String storage = ((RadioButton) findViewById(selectedId)).getText().toString();
            double price;
            try {
                price = Double.parseDouble(priceText);
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Please input valid price", Toast.LENGTH_SHORT).show();
                return;
            }

            // 创建食物对象
            FoodItem newFood = new FoodItem(name, owners, expiry, storage, price);

            // 提交到数据库
            DatabaseHelper.addFood("group123", newFood, new FirebaseCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean data) {
                    Toast.makeText(add_food.this, "添加成功", Toast.LENGTH_SHORT).show();
                    finish(); // 返回上一页
                }

                @Override
                public void onFailure(Exception e) {
                    Toast.makeText(add_food.this, "添加失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

}
