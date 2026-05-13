/*
 * MIT License
 *
 * Copyright (c) 2021 LeeWyatt
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *
 */
package app.controller;

import com.leewyatt.rxcontrols.controls.RXLineButton;
import javafx.application.HostServices;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;

import java.net.URL;
import java.util.ResourceBundle;

public class PaneAboutController {

    @FXML
    private ResourceBundle resources;

    @FXML
    private URL location;


    @FXML
    private RXLineButton giteeLink;

    @FXML
    private RXLineButton githubLink;

    @FXML
    private RXLineButton bilibiliLink;

    @FXML
    void openGithub(ActionEvent event) {
        getHostService().showDocument(githubLink.getText());
    }


    @FXML
    void openGitee(ActionEvent event) {
        getHostService().showDocument(giteeLink.getText());
    }


    @FXML
    void openQQ(ActionEvent event) {
        getHostService().showDocument("http://wpa.qq.com/msgrd?v=3&uin=9670453&site=qq&menu=yes");
    }

    @FXML
    void addGroup(ActionEvent event) {
        getHostService().showDocument("https://qm.qq.com/cgi-bin/qm/qr?k=NiUwB2ugZew91ErCWC7uBw7CaEcBb2_o&jump_from=webapi");
    }

    @FXML
    void openBilibili(ActionEvent event) {
        getHostService().showDocument(bilibiliLink.getText());
    }

    private HostServices getHostService(){
        return  (HostServices) githubLink.getScene().getUserData();
    }

    @FXML
    void initialize() {

    }
}
