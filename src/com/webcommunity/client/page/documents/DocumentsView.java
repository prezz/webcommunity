package com.webcommunity.client.page.documents;

import com.google.gwt.user.client.ui.IsWidget;

public interface DocumentsView extends IsWidget {

	void setPresenter(Presenter presenter);
	void updateDocumentLinks(String[] result);

    public interface Presenter {

		void getDocumentLinks();
    }
}
