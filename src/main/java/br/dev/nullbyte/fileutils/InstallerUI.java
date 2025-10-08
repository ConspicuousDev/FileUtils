package br.dev.nullbyte.fileutils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InstallerUI extends JFrame {
	private static final Logger LOGGER = FileUtils.LOGGER;

	public InstallerUI() {
		initialize();
	}

	private void initialize() {
		this.setTitle(FileUtils.APP_NAME);
		this.setSize(450, 300);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLocationRelativeTo(null);
		this.setResizable(false);

		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BorderLayout(10, 10));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

		JLabel logoLabel = new JLabel(FileUtils.APP_NAME, SwingConstants.CENTER);
		logoLabel.setFont(new Font("Arial", Font.BOLD, 24));
		logoLabel.setIcon(UIManager.getIcon("FileView.fileIcon"));
		logoLabel.setIconTextGap(10);
		mainPanel.add(logoLabel, BorderLayout.NORTH);

		boolean isInstalled = FileUtils.checkInstallation(FileUtils.APP_FOLDER, FileUtils.JAR_NAME);

		// Create center panel with status information
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));

		JLabel statusLabel = new JLabel(isInstalled ?
				"File Utils is already installed." :
				"File Utils is not installed.");
		statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
		statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));

		centerPanel.add(Box.createVerticalGlue());
		centerPanel.add(statusLabel);
		centerPanel.add(Box.createRigidArea(new Dimension(0, 10)));
		centerPanel.add(Box.createVerticalGlue());

		mainPanel.add(centerPanel, BorderLayout.CENTER);

		JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 10, 0));

		if (isInstalled) {
			JButton uninstallButton = new JButton("Uninstall");
			JButton cancelButton = new JButton("Cancel");

			uninstallButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					int confirm = JOptionPane.showConfirmDialog(
							InstallerUI.this,
							"Are you sure you want to uninstall File Utils?",
							"Confirm Uninstallation",
							JOptionPane.YES_NO_OPTION);

					if (confirm == JOptionPane.YES_OPTION) {
						try {
							uninstallButton.setEnabled(false);
							cancelButton.setEnabled(false);
							statusLabel.setText("Uninstalling File Utils...");

							// If we're running the installed version, perform self-uninstall
							File currentJarFile = new File(InstallerUI.class.getProtectionDomain()
									.getCodeSource().getLocation().getPath());
							File installedJarFile = new File(FileUtils.APP_FOLDER, FileUtils.JAR_NAME);

							if (currentJarFile.getAbsolutePath().equals(installedJarFile.getAbsolutePath())) {
								// We are the installed version, perform self-uninstall
								SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
									@Override
									protected Void doInBackground() throws Exception {
										FileUtils.uninstall(FileUtils.APP_FOLDER);
										return null;
									}
								};
								worker.execute();
							} else {
								// We are a separate installer/uninstaller, use process-based uninstall
								SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
									@Override
									protected Void doInBackground() throws Exception {
										// Find path to the installed JAR
										String installedJarPath = FileUtils.APP_FOLDER + "/" + FileUtils.JAR_NAME;

										// Prepare command to execute the installed JAR with uninstall parameter
										ProcessBuilder processBuilder = new ProcessBuilder(
												"javaw",
												"-jar",
												installedJarPath,
												"--uninstall"
										);

										// Redirect error stream to output stream to handle both together
										processBuilder.redirectErrorStream(true);

										LOGGER.log(Level.INFO, "Starting uninstall process with command: javaw -jar " + installedJarPath + " --uninstall");

										// Start the process
										Process process = processBuilder.start();

										// Create a thread to read and log the output
										Thread outputThread = new Thread(() -> {
											try (java.io.BufferedReader reader = new java.io.BufferedReader(
													new java.io.InputStreamReader(process.getInputStream()))) {
												String line;
												while ((line = reader.readLine()) != null) {
													LOGGER.log(Level.INFO, "[Uninstaller] " + line);
												}
											} catch (Exception ex) {
												LOGGER.log(Level.SEVERE, "Error reading uninstaller output", ex);
											}
										});
										outputThread.start();

										// Wait for the process to complete
										int exitCode = process.waitFor();

										// Wait for the output thread to finish reading
										outputThread.join();

										if (exitCode != 0) {
											LOGGER.log(Level.SEVERE, "Uninstaller process exited with code " + exitCode);
											throw new Exception("Uninstaller process exited with code " + exitCode);
										} else {
											LOGGER.log(Level.INFO, "Uninstaller process completed successfully");
										}

										return null;
									}

									@Override
									protected void done() {
										try {
											get(); // Will throw exception if doInBackground threw one
											JOptionPane.showMessageDialog(
													InstallerUI.this,
													"File Utils has been successfully uninstalled.",
													"Uninstallation Complete",
													JOptionPane.INFORMATION_MESSAGE);
											System.exit(0);
										} catch (Exception ex) {
											LOGGER.log(Level.SEVERE, "Uninstallation failed", ex);
											JOptionPane.showMessageDialog(
													InstallerUI.this,
													"Failed to uninstall: " + ex.getMessage(),
													"Uninstallation Error",
													JOptionPane.ERROR_MESSAGE);
											uninstallButton.setEnabled(true);
											cancelButton.setEnabled(true);
											statusLabel.setText("File Utils is already installed.");
										}
									}
								};
								worker.execute();
							}
						} catch (Exception ex) {
							LOGGER.log(Level.SEVERE, "Uninstallation failed", ex);
							JOptionPane.showMessageDialog(
									InstallerUI.this,
									"Failed to uninstall: " + ex.getMessage(),
									"Uninstallation Error",
									JOptionPane.ERROR_MESSAGE);
							uninstallButton.setEnabled(true);
							cancelButton.setEnabled(true);
						}
					}
				}
			});

			cancelButton.addActionListener(e -> System.exit(0));

			buttonPanel.add(uninstallButton);
			buttonPanel.add(cancelButton);
		} else {
			JButton installButton = new JButton("Install");
			JButton cancelButton = new JButton("Cancel");

			installButton.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent e) {
					try {
						installButton.setEnabled(false);
						cancelButton.setEnabled(false);
						statusLabel.setText("Installing File Utils...");

						SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
							@Override
							protected Void doInBackground() throws Exception {
								FileUtils.install(FileUtils.APP_FOLDER, FileUtils.JAR_NAME);
								return null;
							}

							@Override
							protected void done() {
								try {
									get();
									JOptionPane.showMessageDialog(
											InstallerUI.this,
											"File Utils has been successfully installed.",
											"Installation Complete",
											JOptionPane.INFORMATION_MESSAGE);
									System.exit(0);
								} catch (Exception ex) {
									LOGGER.log(Level.SEVERE, "Installation failed", ex);
									StringWriter sw = new StringWriter();
									ex.printStackTrace(new PrintWriter(sw));
									String stackTrace = sw.toString();
									JOptionPane.showMessageDialog(
											InstallerUI.this,
											"Failed to install: " + ex.getMessage() + "\n\n" + stackTrace,
											"Installation Error",
											JOptionPane.ERROR_MESSAGE
									);
									installButton.setEnabled(true);
									cancelButton.setEnabled(true);
									statusLabel.setText("File Utils is not installed.");
								}
							}
						};
						worker.execute();
					} catch (Exception ex) {
						LOGGER.log(Level.SEVERE, "Installation failed", ex);
						JOptionPane.showMessageDialog(
								InstallerUI.this,
								"Failed to install: " + ex.getMessage(),
								"Installation Error",
								JOptionPane.ERROR_MESSAGE);
						installButton.setEnabled(true);
						cancelButton.setEnabled(true);
					}
				}
			});

			cancelButton.addActionListener(e -> System.exit(0));

			buttonPanel.add(installButton);
			buttonPanel.add(cancelButton);
		}

		mainPanel.add(buttonPanel, BorderLayout.SOUTH);

		this.add(mainPanel);
	}
}