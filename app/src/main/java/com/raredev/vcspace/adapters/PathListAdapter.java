package com.raredev.vcspace.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.R;
import com.google.android.material.color.MaterialColors;
import com.raredev.vcspace.databinding.LayoutPathItemBinding;
import com.raredev.vcspace.models.FileModel;
import com.raredev.vcspace.ui.FileTreePopupWindow;
import com.raredev.vcspace.ui.PathListView;
import com.raredev.vcspace.ui.panels.file.FileExplorerPanel;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PathListAdapter extends RecyclerView.Adapter<PathListAdapter.VH> {

  private List<FileModel> paths = new ArrayList<>();

  private FileExplorerPanel fileExplorer;

  private int type;

  public PathListAdapter() {}

  @Override
  public VH onCreateViewHolder(ViewGroup parent, int viewType) {
    return new VH(
        LayoutPathItemBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
  }

  @Override
  public void onBindViewHolder(VH holder, int position) {
    FileModel path = paths.get(position);

    holder.path.setText(path.getName());
    if (position == getItemCount() - 1) {
      holder.path.setTextColor(
          MaterialColors.getColor(holder.itemView.getContext(), R.attr.colorPrimary, 0));
      holder.separator.setVisibility(View.GONE);
    } else {
      holder.path.setTextColor(
          MaterialColors.getColor(holder.itemView.getContext(), R.attr.colorControlNormal, 0));
      holder.separator.setVisibility(View.VISIBLE);
    }

    holder.itemView.setOnClickListener(
        (v) -> {
          if (type == PathListView.TYPE_FILE_PATH) {
            if (!path.isFile())
              new FileTreePopupWindow(holder.itemView.getContext(), v).setPath(path.getPath());
          } else {
            if (position == getItemCount() - 1) {
              return;
            }
            fileExplorer.setCurrentDir(path);
          }
        });
  }

  @Override
  public int getItemCount() {
    return paths.size();
  }

  public void setFileExplorerPanel(FileExplorerPanel fileExplorer) {
    this.fileExplorer = fileExplorer;
  }

  public void setType(int type) {
    this.type = type;
  }

  public void setPath(File path) {
    paths.clear();

    for (; path != null; ) {
      if (path.getPath().equals("/storage/emulated")) {
        break;
      }
      var fileModel = FileModel.fileToFileModel(path);
      if (fileModel.getPath().equals("/storage/emulated/0")) {
        fileModel.setName("sdcard");
      }
      paths.add(fileModel);
      path = path.getParentFile();
    }

    Collections.reverse(paths);
    notifyDataSetChanged();
  }

  class VH extends RecyclerView.ViewHolder {
    TextView path;
    ImageView separator;

    LayoutPathItemBinding bind;

    public VH(LayoutPathItemBinding binding) {
      super(binding.getRoot());
      bind = binding;
      path = binding.path;
      separator = binding.separator;
    }
  }
}