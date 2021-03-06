package org.iplantc.phyloviewer.client.mapper;

import org.iplantc.phyloviewer.shared.model.INode;
import org.iplantc.phyloviewer.shared.model.metadata.ValueMap;
import org.iplantc.phyloviewer.shared.render.style.FilteredStyleMap;
import org.iplantc.phyloviewer.shared.render.style.IStyleMap;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HorizontalPanel;
import com.google.gwt.user.client.ui.InlineLabel;
import com.google.gwt.user.client.ui.Widget;

public class SavedMapperView extends Composite
{
	private static SavedMapperViewUiBinder uiBinder = GWT.create(SavedMapperViewUiBinder.class);
	@UiField HorizontalPanel panel;
	@UiField InlineLabel filterField;
	@UiField InlineLabel styleField;
	@UiField(provided=true) Button removeButton;
	private FilteredStyleMap styleMap;

	interface SavedMapperViewUiBinder extends UiBinder<Widget,SavedMapperView>
	{
	}

	public SavedMapperView(FilteredStyleMap map, Button removeButton)
	{
		this.styleMap = map;
		this.removeButton = removeButton;
		initWidget(uiBinder.createAndBindUi(this));
		ValueMap<INode, Boolean> filter = map.getFilter();
		filterField.setText(filter.toString());
		
		IStyleMap style = map.getPassStyleMap();
		styleField.setText(style.toString());
	}

	public FilteredStyleMap getStyleMap()
	{
		return styleMap;
	}
}
