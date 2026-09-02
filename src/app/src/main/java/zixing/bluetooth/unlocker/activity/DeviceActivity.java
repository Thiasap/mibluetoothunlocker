package zixing.bluetooth.unlocker.activity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.LinkedHashSet;
import java.util.Set;

import butterknife.BindView;
import butterknife.OnClick;
import zixing.bluetooth.unlocker.R;
import zixing.bluetooth.unlocker.adapter.DerviceAdapter;
import zixing.bluetooth.unlocker.adapter.base.BaseRecyclerViewAdapter;
import zixing.bluetooth.unlocker.bean.DeviceBean;
import zixing.bluetooth.unlocker.utils.ArrUtils;
import zixing.bluetooth.unlocker.utils.BluetoothUtils;
import zixing.bluetooth.unlocker.utils.ConfigUtil;
import zixing.bluetooth.unlocker.utils.SPUtils;

public class DeviceActivity extends BaseActivity implements BluetoothUtils.BluetoothInterface{

    @BindView(R.id.recyclerview) RecyclerView mRecyclerView;
    @BindView(R.id.progress) ProgressBar progress;
    @BindView(R.id.fab) FloatingActionButton fab;
    private DerviceAdapter adapter;
    private String sortMode="ASC";

    // 进入时已保存的选择；退出时与当前选中对比判断是否有改动
    private Set<String> savedMacs = new LinkedHashSet<>();
    // 当前选中（点击切换），保持点击顺序
    private Set<String> selectedMacs = new LinkedHashSet<>();
    private boolean exitDialogShowing = false;

    @Override
    public int getLayoutId() {
        return R.layout.activity_add_device;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initView();
        if(!BluetoothUtils.getInstance().isEnabled())
        {
            BluetoothUtils.getInstance().enable();
        }
    }

    @Override
    public void initToolbar() {
        super.initToolbar();
        txtCenterTitle.setText("选择解锁设备");
    }

    @SuppressLint("MissingPermission")
    private void addSelectedDevicesTop() {
        // 已保存/已选中的设备置顶显示，无论是否被扫描到
        BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
        for (String mac : selectedMacs) {
            DeviceBean bean = new DeviceBean();
            bean.setAddress(mac);
            String name = null;
            try {
                BluetoothDevice d = bt.getRemoteDevice(mac);
                if (d != null) name = d.getName();
            } catch (Exception ignored) {
            }
            bean.setName(name == null ? "" : name);
            bean.setRssi(3);
            try {
                BluetoothDevice d = bt.getRemoteDevice(mac);
                bean.setStatus(d != null && d.getBondState() == BluetoothDevice.BOND_BONDED);
            } catch (Exception ignored) {
                bean.setStatus(false);
            }
            adapter.add(bean);
        }
    }

    // 按 address 查找列表位置
    private int indexOfAddress(String address) {
        for (int i = 0; i < adapter.getList().size(); i++) {
            DeviceBean b = adapter.getList().get(i);
            if (b != null && b.getAddress() != null && b.getAddress().equalsIgnoreCase(address)) {
                return i;
            }
        }
        return -1;
    }

    // 用扫描结果更新已有条目数据（保留置顶位置）
    private void mergeDevice(DeviceBean bean) {
        int pos = indexOfAddress(bean.getAddress());
        if (pos < 0) {
            adapter.add(bean);
        } else {
            DeviceBean exist = adapter.getList().get(pos);
            exist.setName(bean.getName());
            exist.setRssi(bean.getRssi());
            exist.setDistance(bean.getDistance());
            exist.setStatus(bean.isStatus());
            adapter.notifyItemChanged(pos);
        }
    }

    @Override
    public void addBluetoothDervice(DeviceBean deviceBeans) {
        mergeDevice(deviceBeans);
    }

    @Override
    public void updateBluetoothDervice(DeviceBean deviceBeans) {
        mergeDevice(deviceBeans);
    }

    @Override
    public void onBluetoothFinish() {
        //搜索结束
        progress.setVisibility(View.GONE);
        int num=adapter.getItemCount();
        Tt("成功搜索到"+num+"个设备");
        if(num>0){
            sortMode="ASC";
            sort();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //注销广播
        BluetoothUtils.getInstance().onDestroy();
    }

    @OnClick({R.id.fab})
    public void onClick(View view) {
        if(BluetoothUtils.getInstance().isDiscovering()){
            return;
        }
        Tt("开始搜索...");
        adapter.clear();//清空搜索历史
        // 重新插入选中设备保持置顶
        addSelectedDevicesTop();
        progress.setVisibility(View.VISIBLE);
        BluetoothUtils.getInstance().startDiscovery();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.sort_menu, menu);
        return true;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        sort();
        return true;
    }

    private void initView() {
        //注册广播
        BluetoothUtils.getInstance().initBluetooth(this);
        //绑定搜索数据回调
        BluetoothUtils.getInstance().setBluetoothListener(this);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        mRecyclerView.setLayoutManager(layoutManager);
        adapter = new DerviceAdapter(this);

        mRecyclerView.setAdapter(adapter);
        DeviceActivity self=this;

        // 进入时载入已保存选择
        savedMacs = new LinkedHashSet<>(ConfigUtil.getMacList(0));
        selectedMacs = new LinkedHashSet<>(savedMacs);
        adapter.setSelectedAddresses(selectedMacs);
        addSelectedDevicesTop();

        adapter.setOnInViewClickListener(R.id.itemCartView, new BaseRecyclerViewAdapter.onInternalClickListener<DeviceBean>() {
            @Override
            public void OnClickListener(View parentV, View v, Integer position, DeviceBean values) {
                DeviceBean bean=(DeviceBean) values;

                self.runOnUiThread(()->{
                    if(SPUtils.isEnableModule==false)
                    {
                        Toast.makeText(self.getApplicationContext(),"请启用模块后再进行操作！",Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (bean == null || bean.getAddress() == null || bean.getAddress().isEmpty()) {
                        return;
                    }
                    // 多选切换
                    String addr = bean.getAddress().toUpperCase();
                    if (selectedMacs.contains(addr)) {
                        selectedMacs.remove(addr);
                    } else {
                        selectedMacs.add(addr);
                    }
                    adapter.notifyDataSetChanged();
                });

            }

            @Override
            public void OnLongClickListener(View parentV, View v, Integer position, DeviceBean values) {

            }
        });

        progress.setVisibility(View.VISIBLE);
        //开始搜索
        BluetoothUtils.getInstance().startDiscovery();
    }

    //排序
    private void sort() {
        if(adapter.getItemCount()==0 || BluetoothUtils.getInstance().isDiscovering())return;
        String mode=sortMode.equals("ASC")?"DESC":"ASC";
        ArrUtils.sortList(adapter.getList(),"rssi",mode);
        sortMode=mode;
        reorderSelectedTop();
        adapter.notifyDataSetChanged();
    }

    // 排序后重新把选中设备放回顶部，保持选择顺序
    private void reorderSelectedTop() {
        adapter.getList().removeIf(b -> b != null && b.getAddress() != null
                && selectedMacs.contains(b.getAddress().toUpperCase()));
        addSelectedDevicesTop();
    }

    @Override
    public void onBackPressed() {
        // 有改动才询问保存；无改动直接退出
        if (!exitDialogShowing && !selectedMacs.equals(savedMacs)) {
            exitDialogShowing = true;
            String joined = String.join(",", selectedMacs);
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("是否选择 "+joined +"作为解锁设备？");
            builder.setCancelable(false);
            builder.setTitle("设备选择");
            builder.setPositiveButton("确定", (dialog, which) -> {
                dialog.dismiss();
                exitDialogShowing = false;
                saveSelection();
            });
            builder.setNegativeButton("取消", (dialog, which) -> {
                dialog.dismiss();
                exitDialogShowing = false;
                finish();
            });
            builder.create().show();
            return;
        }
        super.onBackPressed();
    }

    private void saveSelection() {
        ConfigUtil.setString("mac", String.join(",", selectedMacs));
        Tt("已保存解锁设备");
        finish();
        if (MainActivity.self != null) {
            MainActivity.self.runOnUiThread(()-> MainActivity.self.readConfig());
        }
    }

}
