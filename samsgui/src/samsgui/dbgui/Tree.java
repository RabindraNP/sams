package samsgui.dbgui;

import samscore.ISamsDb;
import sfsys.ISfsys;
import sfsys.ISfsys.*;

import javax.swing.*;
import javax.swing.tree.*;
import javax.swing.event.*;
import java.util.*;
import java.awt.event.*;
import java.awt.Toolkit;
import java.awt.GridLayout;
import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.font.*;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.geom.Rectangle2D;
import java.io.File;

/** 
 * Tree display.
 * @author Carlos A. Rueda
 * @version $Id$ 
 */
public class Tree extends JPanel {
	private static final Color bg_group = new Color(222,222,255);
	private static final Color bg_spectrum = new Color(210,255,255);

	protected DbGui dbgui;
	protected ISfsys imported_fs;
	protected ISfsys computed_fs;
	protected DefaultMutableTreeNode rootNode;
	protected DefaultMutableTreeNode importedNode;
	protected DefaultMutableTreeNode computedNode;
    protected DefaultTreeModel treeModel;
    protected JTree tree;

	protected DefaultTreeCellRenderer tcr;
	protected DefaultMutableTreeNode focusedNode = null;	
	
	public Tree(DbGui dbgui) {
		super(new BorderLayout());
		this.dbgui = dbgui;
		
		JLabel label = new JLabel("Right-click for options", JLabel.CENTER);
		label.setFont(label.getFont().deriveFont(Font.ITALIC));
		label.setForeground(Color.gray);
		add(label, BorderLayout.NORTH);
		
        rootNode = new DefaultMutableTreeNode("Root Node");
		
        treeModel = new DefaultTreeModel(rootNode);

        tree = new JTree(treeModel);

		// To avoid keyboard navigation when "Alt" is pressed. 
		tree.setUI(new javax.swing.plaf.basic.BasicTreeUI() {
			protected KeyListener createKeyListener() {
				return new KeyHandler() {
					public void keyTyped(KeyEvent e) {
						if ( (e.getModifiers() & KeyEvent.ALT_MASK) == KeyEvent.ALT_MASK )
							e.consume();
						else
							super.keyTyped(e);
					}
				};
			}
		});

		tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
		tree.putClientProperty("JTree.lineStyle", "Angled");
		
        add(new JScrollPane(tree));
		
		// cell renderer:
		tree.setCellRenderer(tcr = new MyRenderer());
	}

	public JTree getJTree() {
		return tree;
	}
	
	public Icon getLeafIcon(){
		return tcr.getLeafIcon();
	}
	
	public void setInfo() {
		rootNode.removeAllChildren();

		ISamsDb db = dbgui.getDatabase();
		if ( db != null ) {
			try {
				ISfsys fs = db.getGroupingBy(new String[] {"location"});
				if ( fs != null ) {
					IDirectory group = fs.getRoot();
					rootNode.setUserObject(group);
					createGroupNode(rootNode, group);
				}
			}
			catch (Exception ex) {
				rootNode.setUserObject("error: " +ex.getMessage());
			}
		}
		else
			rootNode.setUserObject("no database");
		
		treeModel.reload();
	}

	public DefaultMutableTreeNode getImportedNode() {
		return importedNode;
	}
	
	public DefaultMutableTreeNode getComputedNode() {
		return computedNode;
	}
	
	private void createGroupNode(DefaultMutableTreeNode parent, IDirectory group) {
		for ( Iterator iter = group.getChildren().iterator(); iter.hasNext(); ) {
			ISfsys.INode node = (ISfsys.INode) iter.next();
			if ( node instanceof ISfsys.IFile ) {
				parent.add(new DefaultMutableTreeNode(node, false));
			}
			else if ( node instanceof ISfsys.IDirectory ) {
				// recurse creating subgroup nodes:
				ISfsys.IDirectory subgroup = (ISfsys.IDirectory) node;
				DefaultMutableTreeNode child = new DefaultMutableTreeNode(subgroup, true);
				createGroupNode(child, subgroup);
				parent.add(child);
				
				String subpath = subgroup.getPath();
				if ( subpath.equals("/computed") )
					computedNode = child;
				else if ( subpath.equals("/imported") )
					importedNode = child;
			}
			else
				throw new RuntimeException("Unexpected object type");
		}
	}
	
	public List getSelectedNodes(Class clazz) {
		return getSelectedNodes(clazz, true);
	}
	
	public List getSelectedNodes(Class clazz, boolean nodes) {
		List list = null;
		TreePath[] paths = tree.getSelectionPaths(); 
		if ( paths != null ) {
			for ( int i = 0; i < paths.length; i++ ) {
				DefaultMutableTreeNode n = (DefaultMutableTreeNode) paths[i].getLastPathComponent();
				Object obj = n.getUserObject();
				if ( clazz.isInstance(obj) ) {
					if ( list == null )
						list = new ArrayList();
					if ( nodes )
						list.add(n);
					else
						list.add(obj);
				}
			}
		}
		return list;
	}
	
    public DefaultMutableTreeNode getFocusedNode() {
		return focusedNode;
	}

    protected void updateFocusedNode(DefaultMutableTreeNode node, int row) {
		if ( focusedNode != node ) {
			focusedNode = node;
			dbgui.focusedNodeChanged();
		}
	}

    public DefaultMutableTreeNode addObject(
		DefaultMutableTreeNode parent,
		Object child,
		boolean shouldBeVisible
	) {
        DefaultMutableTreeNode childNode = new DefaultMutableTreeNode(child);
        if (parent == null) 
            parent = rootNode;
        treeModel.insertNodeInto(childNode, parent, parent.getChildCount());
        if (shouldBeVisible) 
            tree.scrollPathToVisible(new TreePath(childNode.getPath()));
        return childNode;
    }

	class MyRenderer extends DefaultTreeCellRenderer  {
		Font normalFont = null;
		Font boldFont = null;
		
		/**
		 * Returns a wider preferred size value in an attempt to make room for 
		 * the bold font. <b>This is not guaranteed.</b>
		 */
		public Dimension getPreferredSize() {
			Dimension dim = super.getPreferredSize();
			if ( dim != null ) {
				// 16: arbitrarely chosen.
				int new_width = 16 + (int) dim.width;
				dim = new Dimension(new_width, dim.height);
			}
			return dim;
		}

		public Component getTreeCellRendererComponent(
			JTree tree,
			Object value,
			boolean sel,
			boolean expanded,
			boolean leaf,
			int row,
			boolean hasFocus
		) {
			if ( !(value instanceof DefaultMutableTreeNode) ) {
				super.getTreeCellRendererComponent(
					tree, value, sel,
					expanded, leaf, row,
					hasFocus
				);
				return this;
			}

			DefaultMutableTreeNode n = (DefaultMutableTreeNode) value;
			
			if ( hasFocus )
				updateFocusedNode(n, row);

			// do default behaviour:
			super.getTreeCellRendererComponent(
				tree, value, sel,
				expanded, leaf, row,
				hasFocus
			);
			
			value = n.getUserObject();
			if ( value instanceof IDirectory ) {
				setIcon(openIcon);
				setBackgroundSelectionColor(bg_group);
				//setToolTipText("....");
			}
			else if ( value instanceof IFile )  {
				setBackgroundSelectionColor(bg_spectrum);
				//setToolTipText(null); //no tool tip
			}
			else
				System.err.println("Node: " +value.getClass()+ " = " +value);
	
			if ( normalFont == null ) {
				normalFont = getFont();
				if ( normalFont != null )
					boldFont = normalFont.deriveFont(Font.BOLD);
			}
			
			if ( normalFont != null && boldFont != null )
				setFont(hasFocus ? boldFont : normalFont);

			return this;
		}
	}

}