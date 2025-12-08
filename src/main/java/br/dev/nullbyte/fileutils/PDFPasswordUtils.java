package br.dev.nullbyte.fileutils;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;

public class PDFPasswordUtils {
	public static boolean requiresPassword(File file) {
		try (PDDocument doc = PDDocument.load(file)) {
			return doc.isEncrypted();
		} catch (InvalidPasswordException e) {
			return true;
		} catch (IOException e) {
			throw new RuntimeException("Could not check password for file: " + file.getName(), e);
		}
	}

	public static boolean requiresPassword(String filePath) {
		File file = new File(filePath);
		if (!file.exists() || !file.isFile())
			throw new RuntimeException("Invalid file: " + filePath + ".");
		return requiresPassword(new File(filePath));
	}

	public static String requestPassword(File file) {
		boolean incorrectPassword = false;
		while (true) {
			JPanel panel = new JPanel();
			panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

			panel.add(new JLabel("Enter password for:"));

			JLabel fileNameLabel = new JLabel(file.getName());
			fileNameLabel.setBorder(new EmptyBorder(10, 0, 10, 0));
			panel.add(fileNameLabel);

			JPasswordField passwordField = new JPasswordField();
			passwordField.requestFocus();
			panel.add(passwordField);

			if (incorrectPassword) {
				JLabel incorrectPasswordLabel = new JLabel("Incorrect password. Try again.");
				incorrectPasswordLabel.setForeground(Color.RED);
				panel.add(incorrectPasswordLabel);
			}
			JOptionPane optionPane = new JOptionPane(panel, JOptionPane.WARNING_MESSAGE, JOptionPane.OK_CANCEL_OPTION);

			JDialog dialog = optionPane.createDialog("Enter PDF password");
			dialog.setAlwaysOnTop(true);
			dialog.addWindowListener(new java.awt.event.WindowAdapter() {
				public void windowOpened(java.awt.event.WindowEvent e) {
					passwordField.requestFocusInWindow();
				}
			});
			dialog.setVisible(true);
			dialog.dispose();

			Object selectedValue = optionPane.getValue();
			int action = (selectedValue instanceof Integer) ? (Integer) selectedValue : JOptionPane.CLOSED_OPTION;

			if (action != JOptionPane.OK_OPTION)
				throw new AbortException();

			String password = new String(passwordField.getPassword());

			try (PDDocument ignored = PDDocument.load(file, password)) {
				return password;
			} catch (InvalidPasswordException e) {
				incorrectPassword = true;
			} catch (IOException e) {
				FileUtils.LOGGER.warning("Error reading PDF during password check: " + e.getMessage());
				JOptionPane.showMessageDialog(null,
						"Error reading file: " + e.getMessage(),
						"Error",
						JOptionPane.ERROR_MESSAGE);
				throw new AbortException();
			}
		}
	}

	public static String requestPassword(String filePath) {
		File file = new File(filePath);
		if (!file.exists() || !file.isFile())
			throw new RuntimeException("Invalid file: " + filePath + ".");
		return requestPassword(new File(filePath));
	}
}
