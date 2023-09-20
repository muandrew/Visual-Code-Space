package com.raredev.vcspace.utils;

import android.content.Context;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.reflect.TypeToken;
import com.raredev.vcspace.models.DocumentModel;
import com.raredev.vcspace.ui.panels.Panel;
import com.raredev.vcspace.ui.panels.PanelArea;
import com.raredev.vcspace.ui.panels.editor.EditorPanel;
import com.raredev.vcspace.ui.panels.file.FileExplorerPanel;
import com.raredev.vcspace.ui.panels.web.WebViewPanel;
import com.raredev.vcspace.ui.panels.welcome.WelcomePanel;
import java.util.ArrayList;
import java.util.List;

public class PanelUtils {

  public static final String TYPE_WELCOME = "WelcomePanel";
  public static final String TYPE_EDITOR = "EditorPanel";
  public static final String TYPE_EXPLORER = "FileExplorerPanel";
  public static final String TYPE_WEBVIEW = "WebViewPanel";

  public static String panelsToJson(List<Panel> panels) {
    if (panels.isEmpty()) return "";
    List<LinkedTreeMap<String, String>> panelMapList = new ArrayList<>();
    Gson gson = new Gson();
    for (Panel panel : panels) {
      LinkedTreeMap<String, String> map = new LinkedTreeMap<>();
      map.put("type", panel.getClass().getSimpleName());
      map.put("selected", String.valueOf(panel.isSelected()));
      map.put("pinned", String.valueOf(panel.isPinned()));

      if (panel instanceof EditorPanel) {
        map.put("document", gson.toJson(((EditorPanel) panel).getDocument()));
      }

      if (panel instanceof FileExplorerPanel) {
        map.put("currentPath", ((FileExplorerPanel) panel).getCurrentDir().getPath());
      }

      if (panel instanceof WebViewPanel) {
        var webViewPanel = (WebViewPanel) panel;
        map.put("filePath", webViewPanel.getFilePath());
        map.put("supportZoom", String.valueOf(webViewPanel.isSupportZoom()));
        map.put("desktopMode", String.valueOf(webViewPanel.isDesktopMode()));
      }
      panelMapList.add(map);
    }
    return gson.toJson(panelMapList);
  }

  public static void addJsonPanelsInArea(Context context, String json, PanelArea panelArea) {
    Gson gson = new Gson();

    var typeToken = new TypeToken<List<LinkedTreeMap<String, String>>>() {}.getType();

    List<LinkedTreeMap<String, String>> panelMapList = gson.fromJson(json, typeToken);
    if (panelMapList == null) {
      return;
    }
    
    for (LinkedTreeMap<String, String> panelMap : panelMapList) {
      String type = panelMap.get("type");

      Panel panel = createPanel(context, type);
      if (type.equals(TYPE_EDITOR)) {
        DocumentModel document = gson.fromJson(panelMap.get("document"), DocumentModel.class);
        panel = new EditorPanel(context, document);
      }
      panel.setPinned(Boolean.parseBoolean(panelMap.get("pinned")));

      panelArea.addPanel(panel, Boolean.parseBoolean(panelMap.get("selected")));

      if (type.equals(TYPE_EXPLORER)) {
        ((FileExplorerPanel) panel).setCurrentDir(panelMap.get("currentPath"));
      }

      if (type.equals(TYPE_WEBVIEW)) {
        var webViewPanel = (WebViewPanel) panel;
        webViewPanel.loadFile(panelMap.get("filePath"));
        webViewPanel.setSupportZoom(Boolean.parseBoolean(panelMap.get("supportZoom")));
        webViewPanel.setDesktopMode(Boolean.parseBoolean(panelMap.get("desktopMode")));
      }
    }
  }

  public static String getUniqueTabTitle(EditorPanel selectedPanel, List<Panel> panels) {
    int count = 0;
    UniqueNameBuilder<EditorPanel> builder = new UniqueNameBuilder<>("", "/");

    for (Panel panel : panels) {
      if (panel instanceof EditorPanel) {
        var doc = ((EditorPanel) panel).getDocument();
        if (doc.getName().equals(selectedPanel.getDocument().getName())) {
          count++;
        }
        builder.addPath((EditorPanel) panel, doc.getPath());
      }
    }
    if (count > 1) {
      return builder.getShortPath(selectedPanel);
    } else {
      return selectedPanel.getDocument().getName();
    }
  }

  private static Panel createPanel(Context context, String type) {
    switch (type) {
      case TYPE_WELCOME:
        return new WelcomePanel(context);
      case TYPE_EXPLORER:
        return new FileExplorerPanel(context);
      case TYPE_WEBVIEW:
        return new WebViewPanel(context);
      default:
        return null;
    }
  }
}