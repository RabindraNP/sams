package samsgui.dbgui;

import samsgui.SamsGui;
import samsgui.BaseDialog;

import samscore.ISamsDb;
import samscore.ISamsDb.ISpectrum;
import samscore.Sams;
import sfsys.ISfsys;
import sfsys.ISfsys.*;
import sig.Signature;
import sigoper.*;

import javax.swing.tree.*;
import javax.swing.*;
import javax.swing.event.*;
import java.awt.event.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import javax.swing.Timer;


/**
 * Interacts with the user to perform an available operation.
 * @author Carlos A. Rueda
 * @version $Id$
 */ 
public class Compute {
	static final int ONE_SECOND = 1000;
	static final String lbl_in_place = "In place";
	static final String lbl_new_signature = "New signature";
	static final String lbl_name_suffix = "Suffix for new name";
	static final String lbl_ignore_suffix = "Ignore original suffix";

	static String ignore_suffix_value = "";   // to remember
	
	private DbGui dbgui;
	private IOperation sigOper;
	private IOperation.IParameterInfo parInfo;
	private Map parValues;
	private List selectedSpectra;
	private Signature reference_sig;
	private DefaultMutableTreeNode computedNode;
	
	
	public Compute(DbGui dbgui, IOperation sigOper, List selectedSpectra, Signature reference_sig) {
		this.dbgui = dbgui;
		this.sigOper = sigOper;
		this.selectedSpectra = selectedSpectra;
		this.reference_sig = reference_sig;
		computedNode = dbgui.getTree().getComputedNode();
		if ( sigOper instanceof IMultiSignatureOperation )
			new MultiForm((IMultiSignatureOperation) sigOper).go();
		else
			new SingleForm(sigOper).go();
	}

    static void doUpdate(Runnable r) {
        try {
            SwingUtilities.invokeAndWait(r);
        }
        catch (Exception e) {
            System.err.println(e);
        }
    }

	void addParInfoComponents(List array) {
		if ( parInfo == null )
			return;
		parValues = new HashMap();
		int numpars = parInfo.getNumParameters();
		for ( int i = 0; i < numpars; i++ ) {
			final String pname = parInfo.getName(i);
			String pdesc = parInfo.getDescription(i);
			Object pval = parInfo.getValue(i);
			if ( pval instanceof String ) {
				String str = (String) pval;
				parValues.put(pname, str);
				JTextField cmp = new JTextField(str);
				cmp.setName("::" +pname);
				cmp.setBorder(SamsGui.createTitledBorder(pdesc));
				array.add(cmp);
			}
			else if ( pval instanceof String[] ) {
				String[] values = (String[]) pval;
				parValues.put(pname, values[0]);
				JComboBox cmp = new JComboBox(values);
				cmp.setName("::" +pname);
				cmp.setSelectedItem(values[0]);
				cmp.setBorder(SamsGui.createTitledBorder(pdesc));
				array.add(cmp);
			}
			else if ( pval instanceof Boolean ) {
				parValues.put(pname, pval);
				boolean defaultValue = ((Boolean) pval).booleanValue();
				JCheckBox cmp = new JCheckBox(pdesc, defaultValue);
				cmp.setName("::" +pname);
				array.add(cmp);
			}
		}
	}

	// All "string" parameters are required
	String _checkRequiredParameters() {
		if ( parInfo != null ) {
			int numpars = parInfo.getNumParameters();
			for ( int i = 0; i < numpars; i++ ) {
				String pname = parInfo.getName(i);
				String pdesc = parInfo.getDescription(i);
				Object obj = parValues.get(pname);
				if ( obj instanceof String ) {
					String pval = (String) obj;
					if ( pval.trim().length() == 0 )
						return "parameter required: " +pdesc;
				}
			}
		}
		return null;
	}
	
	// only for operation parameters:
	void par_notifyUpdate(String comp_name, Object value) {
		if ( comp_name != null && comp_name.startsWith("::") )
			parValues.put(comp_name.substring(2), value);
	}
	
	abstract class BaseForm {
		StringBuffer task_message = new StringBuffer();
		boolean task_isDone;
		Timer timer;
		JProgressBar progressBar = new JProgressBar(0, 1000);
		JTextArea taskOutput = new JTextArea(5, 30);
		JTextField f_resultname;
		JLabel status = new JLabel();
		
		BaseForm() {
			status.setFont(status.getFont().deriveFont(Font.ITALIC));
			progressBar.setValue(0);
			progressBar.setStringPainted(true);
			progressBar.setString("");
			progressBar.setEnabled(false);
			taskOutput.setMargin(new Insets(5,5,5,5));
			taskOutput.setEditable(false);
			taskOutput.setEnabled(false);
			taskOutput.setBackground(null);
			timer = new Timer(ONE_SECOND, new ActionListener() {
				public void actionPerformed(ActionEvent evt) {
					String s = task_message.toString();
					task_message.setLength(0);
					if ( s.length() > 0 ) {
						String last = s.substring(s.lastIndexOf('\n') + 1); 
						taskOutput.append(s + "\n");
						taskOutput.setCaretPosition(taskOutput.getDocument().getLength());
					}
					if ( task_isDone ) {
						Toolkit.getDefaultToolkit().beep();
						timer.stop();
					}
				}
			});
		}
			
	}
	class MultiForm extends BaseForm {
		IMultiSignatureOperation sigOper;
		
		MultiForm(IMultiSignatureOperation sigOper) {
			super();
			this.sigOper = sigOper;
			parInfo = sigOper.getParameterInfo();
		}
		
		void go() {
			JFrame frame = dbgui.getFrame();
			final ISamsDb db = dbgui.getDatabase();
			if ( db == null )
				return;
			
			f_resultname = new JTextField(20);
			f_resultname.setBorder(SamsGui.createTitledBorder("Computed signature name"));
			
			List array = new ArrayList();
			array.add(f_resultname);
			addParInfoComponents(array);
			array.add(status);
			array.add(progressBar);
			array.add(new JScrollPane(taskOutput));
			
			final BaseDialog form = new BaseDialog(frame, sigOper.getName(), array.toArray()) {
				public boolean dataOk() {
					String msg = null;
					String resultname = f_resultname.getText();
					if ( resultname.trim().length() == 0 )
						msg = "Please specify a name for resulting signature";
					if ( msg == null )
						msg = _checkRequiredParameters();
					
					if ( msg == null ) {
						status.setForeground(Color.gray);
						status.setText("OK");
					}
					else {
						status.setForeground(Color.red);
						status.setText(msg);
					}
					return msg == null;
				}
				
				public void notifyUpdate(String comp_name, Object value) {
					par_notifyUpdate(comp_name, value);
					if ( !timer.isRunning() )
						super.notifyUpdate(comp_name, value);
				}
				
				int successful;
				
				public boolean preAccept() {
					if ( task_isDone )
						return true;
					
					if ( !dataOk() )
						return false;
					
					if ( parInfo != null ) {
						int numpars = parInfo.getNumParameters();
						for ( int i = 0; i < numpars; i++ ) {
							String pname = parInfo.getName(i);
							parInfo.setValue(i, parValues.get(pname));
						}
					}
					final String resultname = f_resultname.getText();
					
					// do computation:
					Thread thread = new Thread(new Runnable() {
						public void run() {
							doUpdate(new Runnable() {
								public void run() {
									f_resultname.setEditable(false);
									btnAccept.setEnabled(false);
									btnCancel.setEnabled(false);
									progressBar.setEnabled(true);
									taskOutput.setEnabled(true);
								}
							});
	
							progressBar.setMaximum(selectedSpectra.size() +2);
							progressBar.setIndeterminate(false);
							progressBar.setString(null); //display % string

							try {	
								Signature[] sigs = getSignatures();
								
								// a)
								task_message.append("\nComputing...");
								progressBar.setValue(progressBar.getMaximum() -2);
								Signature sig = sigOper.operate(sigs);
								String path = "/computed/" +resultname;
								
								// b)
								task_message.append("\nAdding result " +path);
								progressBar.setValue(progressBar.getMaximum() -1);
								ISpectrum s = db.addSpectrum(path, sig);
								final IFile f = (IFile) db.getGroupingLocation().getRoot().findNode(s.getPath());

								// update GUI
								doUpdate(new Runnable() {
									public void run() {
										dbgui.getTree().addObject(computedNode, f, true);
										dbgui.refreshTable();
									}
								});
								progressBar.setValue(progressBar.getMaximum());
								
								task_message.append("\nDone.");
							}
							catch(RuntimeException ex) {
								task_message.append("\nRuntimeException!: " +ex.getMessage());
								ex.printStackTrace();
							}
							catch(Exception ex) {
								task_message.append("\nError: " +ex.getMessage());
								progressBar.setString("An error ocurred");
							}
							finally {
								task_isDone = true;
								btnAccept.setText("Close");
								btnAccept.setEnabled(true);
							}
						}
					});
					
					progressBar.setString("Starting...");
					progressBar.setIndeterminate(true);
					thread.start();
					timer.start();
					return false;
				}
			};
			form.activate();
			form.pack();
			form.setLocationRelativeTo(frame);
			form.setVisible(true);
		}
		
		Signature[] getSignatures() throws Exception {
			Signature[] sigs = new Signature[selectedSpectra.size()];
			for ( int i = 0; i < selectedSpectra.size(); i++ ) {
				IFile f = (IFile) selectedSpectra.get(i);
				String path = f.getPath();
				task_message.append("processing " +path+ "\n");
				progressBar.setValue(i+1);
				Signature sig = dbgui.getDatabase().getSignature(path);
				if ( sig.getUserObject() == null )
					sig.setUserObject(f.getName());
				sigs[i] = sig;
			}
			return sigs;
		}
		
	}
	
	class SingleForm extends BaseForm {
		IOperation sigOper;
		
		SingleForm(IOperation sigOper) {
			super();
			this.sigOper = sigOper;
			parInfo = sigOper.getParameterInfo();
		}
		
		void go() {
			JFrame frame = dbgui.getFrame();
			final ISamsDb db = dbgui.getDatabase();
			if ( db == null )
				return;
			
			final JRadioButton r_inplace = new JRadioButton("Apply operation in-place");
			r_inplace.setName("inplace");
			r_inplace.setAlignmentX(0f);
			r_inplace.setMnemonic(KeyEvent.VK_I);
			r_inplace.setSelected(true);
			final JRadioButton r_create = new JRadioButton("Create new resulting signatures with suffix");
			r_create.setName("create");
			r_create.setAlignmentX(0f);
			r_create.setMnemonic(KeyEvent.VK_C);
			r_create.setSelected(false);
			JPanel panel_create = new JPanel(new FlowLayout(FlowLayout.LEFT));
			panel_create.setAlignmentX(0f);
			panel_create.add(r_create);
			f_resultname = new JTextField(10);
			f_resultname.setName("suffix");
			panel_create.add(f_resultname);
			
			ButtonGroup group = new ButtonGroup();
			group.add(r_inplace);
			group.add(r_create);
			
			List array = new ArrayList();
			array.add(r_inplace);
			array.add(panel_create);
			addParInfoComponents(array);
			array.add(status);
			array.add(progressBar);
			array.add(new JScrollPane(taskOutput));
			
			final BaseDialog form = new BaseDialog(frame, sigOper.getName(), array.toArray()) {
				public boolean dataOk() {
					String msg = null;
					if ( r_create.isSelected() ) {
						String resultname = f_resultname.getText();
						if ( resultname.trim().length() == 0 )
							msg = "Please specify a suffix to compose names";
					}
					if ( msg == null )
						msg = _checkRequiredParameters();
					
					if ( msg == null ) {
						status.setForeground(Color.gray);
						status.setText("OK");
					}
					else {
						status.setForeground(Color.red);
						status.setText(msg);
					}
					return msg == null;
				}
				
				public void notifyUpdate(String comp_name, Object value) {
					if ( comp_name != null ) { 
						if ( comp_name.equals("suffix") ) 
							r_create.setSelected(true);
						else
							par_notifyUpdate(comp_name, value);
					}
					if ( !timer.isRunning() )
						super.notifyUpdate(comp_name, value);
				}
				
				int successful;
				
				public boolean preAccept() {
					if ( task_isDone )
						return true;
					
					if ( !dataOk() )
						return false;
					
					if ( parInfo != null ) {
						int numpars = parInfo.getNumParameters();
						for ( int i = 0; i < numpars; i++ ) {
							String pname = parInfo.getName(i);
							Object pval = parValues.get(pname);
							parInfo.setValue(i, pval);
						}
					}
					
					// do computation:
					Thread thread = new Thread(new Runnable() {
						public void run() {
							doUpdate(new Runnable() {
								public void run() {
									f_resultname.setEditable(false);
									btnAccept.setEnabled(false);
									btnCancel.setEnabled(false);
									progressBar.setEnabled(true);
									taskOutput.setEnabled(true);
								}
							});
	
							progressBar.setMaximum(selectedSpectra.size() +1);
							progressBar.setIndeterminate(false);
							progressBar.setString(null); //display % string

							try {	
								for ( int i = 0; i < selectedSpectra.size(); i++ ) {
									IFile f = (IFile) selectedSpectra.get(i);
									String path = f.getPath();
									task_message.append("processing " +path+ "\n");
									progressBar.setValue(i+1);
									Signature sig = dbgui.getDatabase().getSignature(path);
									Signature sig_res;
									if ( sigOper instanceof IBinarySignatureOperation )
										sig_res = ((IBinarySignatureOperation) sigOper).operate(sig, reference_sig);
									else
										sig_res = ((ISingleSignatureOperation) sigOper).operate(sig);
									
									if ( r_create.isSelected() ) {
										String prefix = f.getName();
										if ( prefix.endsWith(".txt") )
											prefix = prefix.substring(0, prefix.length() - ".txt".length());
										String resultname = f_resultname.getText();
										String path_res = "/computed/" +prefix+resultname;
										
										task_message.append("Adding result " +path_res+ "\n");
										ISpectrum s = db.addSpectrum(path_res, sig_res);
										f = (IFile) db.getGroupingLocation().getRoot().findNode(s.getPath());
										dbgui.getTree().addObject(computedNode, f, false);
									}
									else {
										db.setSignature(path, sig_res);
									}
								}

								// update GUI
								doUpdate(new Runnable() {
									public void run() {
										if ( r_inplace.isSelected() )
											dbgui.plotSelectedSignatures(true);
										else
											dbgui.refreshTable();
									}
								});
								progressBar.setValue(progressBar.getMaximum());
								
								task_message.append("\nDone.");
							}
							catch(RuntimeException ex) {
								task_message.append("\nRuntimeException!: " +ex.getMessage());
								ex.printStackTrace();
							}
							catch(Exception ex) {
								task_message.append("\nError: " +ex.getMessage());
								progressBar.setString("An error ocurred");
							}
							finally {
								task_isDone = true;
								btnAccept.setText("Close");
								btnAccept.setEnabled(true);
							}
						}
					});
					
					progressBar.setString("Starting...");
					progressBar.setIndeterminate(true);
					thread.start();
					timer.start();
					return false;
				}
			};
			form.activate();
			form.pack();
			form.setLocationRelativeTo(frame);
			form.setVisible(true);
		}
	}
	
}
