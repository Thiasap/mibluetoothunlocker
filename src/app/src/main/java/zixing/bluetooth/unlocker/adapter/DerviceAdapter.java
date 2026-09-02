package zixing.bluetooth.unlocker.adapter;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import butterknife.BindView;
import butterknife.ButterKnife;
import zixing.bluetooth.unlocker.R;
import zixing.bluetooth.unlocker.adapter.base.BaseRecyclerViewAdapter;
import zixing.bluetooth.unlocker.bean.DeviceBean;

public class DerviceAdapter extends BaseRecyclerViewAdapter<DeviceBean> {

    // 选中卡片高亮色（浅蓝）；设备选择页多选用，首页传空集合即无高亮
    private static final int COLOR_SELECTED = 0xFFBBDEFB;

    private Set<String> selectedAddresses = new HashSet<>();

    public DerviceAdapter(Context context) {
        super(new ArrayList<>(),context);
    }

    public void setSelectedAddresses(Set<String> addresses) {
        this.selectedAddresses = addresses == null ? new HashSet<>() : addresses;
        notifyDataSetChanged();
    }

    public boolean isSelected(DeviceBean data) {
        return data != null && data.getAddress() != null
                && selectedAddresses.contains(data.getAddress().toUpperCase());
    }
    @Override
    protected Animator[] getAnimators(View view) {
        if (view.getMeasuredHeight() <=0){
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1.05f, 1.0f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1.05f, 1.0f);
            return new ObjectAnimator[]{scaleX, scaleY};
        }
        return new Animator[]{
                ObjectAnimator.ofFloat(view, "scaleX", 1.05f, 1.0f),
                ObjectAnimator.ofFloat(view, "scaleY", 1.05f, 1.0f),
        };
    }


    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        mContext = parent.getContext();
        View view = LayoutInflater.from(mContext).inflate(R.layout.item_dervice, parent, false);
        return new MyViewHolder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int position) {
        super.onBindViewHolder(viewHolder, position);
        MyViewHolder holder = (MyViewHolder) viewHolder;
        DeviceBean data = list.get(position);
        if (data == null) return;
        // 手动输入不存在的 MAC 时 getName/getAddress 可能为 null，兜底避免闪退
        String name = data.getName();
        holder.txtAddress.setText(name == null || name.isEmpty() ? "Unknown" : name);
        String addr = data.getAddress();
        holder.txtMac.setText(addr == null || addr.isEmpty() ? "Unknown" : addr);
        if(data.getRssi()<1)
        {
            holder.txtRssi.setText(data.getRssi()+"dB");
            holder.txtTime.setText(String.format("%.2f", data.getDistance())+"m");
        }
        else{
            holder.txtRssi.setText("Unknown");
            holder.txtTime.setText("Unknown");
        }
        // rssi >= 1 表示未测距（未知），信号图标按最弱显示，避免误显示满格
        holder.imageSignal.setImageResource(getRssiIcon(data.getRssi() >= 1 ? -120 : data.getRssi()));
        holder.txtDesc.setVisibility(data.isStatus()?View.VISIBLE:View.GONE);
        holder.cardView.setCardBackgroundColor(isSelected(data) ? COLOR_SELECTED : Color.WHITE);
        animate(viewHolder, position);
    }

    public static int getRssiIcon(int rssi){
        if (rssi >= -50){
            return R.mipmap.ic_rssi5;
        }else if (rssi >= -62){
            return R.mipmap.ic_rssi4;
        }else if (rssi >= -74){
            return R.mipmap.ic_rssi3;
        }else if (rssi >= -89){
            return R.mipmap.ic_rssi2;
        }else {
            return R.mipmap.ic_rssi1;
        }
    }

    public static class MyViewHolder extends RecyclerView.ViewHolder {
        @BindView(R.id.txtAddress)
        public TextView txtAddress;
        @BindView(R.id.txtMac )
        public TextView txtMac;
        @BindView(R.id.txtRssi)
        public TextView txtRssi;
        @BindView(R.id.txtTime)
        public TextView txtTime;
        @BindView(R.id.txtDesc)
        public TextView txtDesc;
        @BindView(R.id.imageSignal)
        public ImageView imageSignal;
        @BindView(R.id.itemCartView)
        public CardView cardView;

        public MyViewHolder(View view) {
            super(view);
            ButterKnife.bind(this, view);
        }
    }



}
