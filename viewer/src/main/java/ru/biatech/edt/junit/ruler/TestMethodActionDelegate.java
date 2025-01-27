/*******************************************************************************
 * Copyright (c) 2022 BIA-Technologies Limited Liability Company.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package ru.biatech.edt.junit.ruler;

import com._1c.g5.v8.dt.bsl.model.Module;
import com._1c.g5.v8.dt.bsl.ui.editor.BslXtextDocument;
import com._1c.g5.v8.dt.bsl.ui.editor.BslXtextEditor;
import com._1c.g5.v8.dt.metadata.mdclass.CommonModule;
import com._1c.g5.v8.dt.ui.editor.IDtGranularEditorEmbeddedEditorPage;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.text.source.IVerticalRulerInfo;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.ui.IActionDelegate2;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.texteditor.AbstractRulerActionDelegate;
import org.eclipse.ui.texteditor.ITextEditor;
import org.eclipse.ui.texteditor.MarkerUtilities;
import ru.biatech.edt.junit.TestViewerPlugin;
import ru.biatech.edt.junit.launcher.v8.LaunchHelper;
import ru.biatech.edt.junit.ui.JUnitMessages;

public class TestMethodActionDelegate extends AbstractRulerActionDelegate implements IActionDelegate2 {

  private static final String MODE_ATTRIBUTE = "mode";
  
  private final int fDoubleClickTime = TestViewerPlugin.ui().getActiveWorkbenchShell().getDisplay().getDoubleClickTime();
  protected IAction action;
  long fMouseUpDelta;
  boolean fDoubleClicked;
  BslXtextEditor bslXtextEditor;
  Menu testMethodMenu;

  @Override
  public void selectionChanged(IAction action, ISelection selection) {
  }

  @Override
  public void dispose() {
    testMethodMenu.dispose();
    super.dispose();
  }

  @Override
  public void setActiveEditor(IAction callerAction, IEditorPart targetEditor) {
    action = null;
    if (targetEditor instanceof IDtGranularEditorEmbeddedEditorPage<?>) {
      var editor = ((IDtGranularEditorEmbeddedEditorPage<?>) targetEditor).getEmbeddedEditor();
      bslXtextEditor = (BslXtextEditor) editor;
    } else {
      bslXtextEditor = null;
    }
    super.setActiveEditor(callerAction, targetEditor);
  }

  @Override
  protected IAction createAction(ITextEditor editor, IVerticalRulerInfo rulerInfo) {
    action = new OpenTestMenuAction();
    return action;
  }

  @Override
  public void mouseDoubleClick(MouseEvent e) {
    if (1 == e.button) {
      fDoubleClicked = true;
    }
  }

  @Override
  public void mouseDown(MouseEvent e) {
    fMouseUpDelta = System.currentTimeMillis();
    fDoubleClicked = false;
    super.mouseDown(e);
  }

  @Override
  public void mouseUp(MouseEvent mouseEvent) {
    final int delay = fMouseUpDelta == 0 ? 0 : fDoubleClickTime - (int) (System.currentTimeMillis() - fMouseUpDelta);
    if (1 != mouseEvent.button)
      return;
    IMarker marker = getMarker();
    if (marker == null) {
      return;
    }
    Runnable runnable = () -> {
      if (!fDoubleClicked && action != null) {
        Event event = new Event();
        event.x = mouseEvent.x;
        event.y = mouseEvent.y;
        event.data = marker;
        event.display = mouseEvent.display;
        action.runWithEvent(event);
      }
    };
    if (delay <= 0)
      runnable.run();
    else
      mouseEvent.widget.getDisplay().timerExec(delay, runnable);
  }

  protected Menu getMenu() {
    if (testMethodMenu == null) {
      testMethodMenu = createMenu();
    }
    return testMethodMenu;
  }

  Menu createMenu() {
    Menu menu = new Menu(bslXtextEditor.getShell(), SWT.POP_UP);

    var listener = new SelectionAdapter() {
      @Override
      public void widgetSelected(SelectionEvent selectionEvent) {
        String mode = (String) selectionEvent.widget.getData(MODE_ATTRIBUTE);
        String method = (String) selectionEvent.widget.getData(RulerAttributes.ATTRIBUTE_METHOD);
        String module = ((CommonModule) getModel().getOwner()).getName();

        LaunchHelper.runTestMethod(module + "." + method, mode); //$NON-NLS-1$
      }
    };

    MenuItem menuItem = new MenuItem(menu, SWT.NONE);
    menuItem.setText(JUnitMessages.TestMethodActionDelegate_Run);
    menuItem.setData(MODE_ATTRIBUTE, ILaunchManager.RUN_MODE);
    menuItem.setImage(TestViewerPlugin.ui().createImage("etool16/run_exc.png")); //$NON-NLS-1$
    menuItem.addSelectionListener(listener);

    menuItem = new MenuItem(menu, SWT.NONE);
    menuItem.setText(JUnitMessages.TestMethodActionDelegate_Debug);
    menuItem.setData(MODE_ATTRIBUTE, ILaunchManager.DEBUG_MODE);
    menuItem.setImage(TestViewerPlugin.ui().createImage("etool16/debug_exc.png")); //$NON-NLS-1$
    menuItem.addSelectionListener(listener);

    return menu;
  }

  void showMenu(Event event) {
    IMarker marker = (IMarker) event.data;
    String method = marker.getAttribute(RulerAttributes.ATTRIBUTE_METHOD, (String)null);
    Menu menu = getMenu();
    for (MenuItem item : menu.getItems()) {
      item.setData(RulerAttributes.ATTRIBUTE_METHOD, method);
    }

    Point point = event.display.getCursorLocation();
    menu.setLocation(point.x - 5, point.y - 5);
    menu.setVisible(true);
  }

  Module getModel() {
    return getDocument().readOnlyDataModel(state -> {
      EObject root = state.getParseResult().getRootASTElement();
      return (Module) root;
    });
  }

  BslXtextDocument getDocument() {
    return (BslXtextDocument) bslXtextEditor.getDocumentProvider().getDocument(bslXtextEditor.getEditorInput());
  }

  IMarker[] getMarkers() {
    try {
      return bslXtextEditor.getResource().findMarkers(RulerAttributes.MARKER_ID, false, 0);
    } catch (CoreException e) {
      TestViewerPlugin.log().logError(JUnitMessages.TestMethodActionDelegate_CollectingMarkers, e);
    }
    return null;
  }

  IMarker getMarker() {
    int line = getRulerInfo().getLineOfLastMouseButtonActivity() + 1;
    for (IMarker marker : getMarkers()) {
      if (MarkerUtilities.getLineNumber(marker) == line) {
        return marker;
      }
    }
    return null;
  }

  IVerticalRulerInfo getRulerInfo() {
    return bslXtextEditor.getAdapter(IVerticalRulerInfo.class);
  }

  class OpenTestMenuAction extends Action {
    @Override
    public void run() {
      super.run();
    }

    @Override
    public void runWithEvent(Event event) {
      showMenu(event);
    }

    @Override
    public boolean isEnabled() {
      return true;
    }

    @Override
    public String getText() {
      return null;
    }

  }
}
