package io.github.leewyatt.rxcontrols.samples.sampler;

import io.github.leewyatt.rxcontrols.RXAccordion;
import io.github.leewyatt.rxcontrols.RXAudioSpectrum;
import io.github.leewyatt.rxcontrols.RXAutoCompleteField;
import io.github.leewyatt.rxcontrols.RXAutoCompletion;
import io.github.leewyatt.rxcontrols.RXAvatar;
import io.github.leewyatt.rxcontrols.RXBackdrop;
import io.github.leewyatt.rxcontrols.RXBadge;
import io.github.leewyatt.rxcontrols.RXBarSpinner;
import io.github.leewyatt.rxcontrols.RXBreadcrumbBar;
import io.github.leewyatt.rxcontrols.RXBursts;
import io.github.leewyatt.rxcontrols.RXButton;
import io.github.leewyatt.rxcontrols.RXCalendarView;
import io.github.leewyatt.rxcontrols.RXCarousel;
import io.github.leewyatt.rxcontrols.RXCascader;
import io.github.leewyatt.rxcontrols.RXCascaderView;
import io.github.leewyatt.rxcontrols.RXCheckBox;
import io.github.leewyatt.rxcontrols.RXChip;
import io.github.leewyatt.rxcontrols.RXChipInput;
import io.github.leewyatt.rxcontrols.RXChipSet;
import io.github.leewyatt.rxcontrols.RXCircularProgressIndicator;
import io.github.leewyatt.rxcontrols.RXClipPathImageView;
import io.github.leewyatt.rxcontrols.RXCopyButton;
import io.github.leewyatt.rxcontrols.RXDatePicker;
import io.github.leewyatt.rxcontrols.RXDateRangePicker;
import io.github.leewyatt.rxcontrols.RXDateRangeView;
import io.github.leewyatt.rxcontrols.RXDateTimePicker;
import io.github.leewyatt.rxcontrols.RXDateTimeRangePicker;
import io.github.leewyatt.rxcontrols.RXDateTimeView;
import io.github.leewyatt.rxcontrols.RXDecimalField;
import io.github.leewyatt.rxcontrols.RXDialog;
import io.github.leewyatt.rxcontrols.RXDigit;
import io.github.leewyatt.rxcontrols.RXDotPulse;
import io.github.leewyatt.rxcontrols.RXDoubleField;
import io.github.leewyatt.rxcontrols.RXDrawerPane;
import io.github.leewyatt.rxcontrols.RXDualPane;
import io.github.leewyatt.rxcontrols.RXFillButton;
import io.github.leewyatt.rxcontrols.RXFillLabel;
import io.github.leewyatt.rxcontrols.RXHighlightTextView;
import io.github.leewyatt.rxcontrols.RXHoldButton;
import io.github.leewyatt.rxcontrols.RXImagePane;
import io.github.leewyatt.rxcontrols.RXImageView;
import io.github.leewyatt.rxcontrols.RXIntegerField;
import io.github.leewyatt.rxcontrols.RXKanbanView;
import io.github.leewyatt.rxcontrols.RXLineButton;
import io.github.leewyatt.rxcontrols.RXLineLabel;
import io.github.leewyatt.rxcontrols.RXListView;
import io.github.leewyatt.rxcontrols.RXLongField;
import io.github.leewyatt.rxcontrols.RXLrcLineView;
import io.github.leewyatt.rxcontrols.RXLrcView;
import io.github.leewyatt.rxcontrols.RXMasonryView;
import io.github.leewyatt.rxcontrols.RXMaterialDecimalField;
import io.github.leewyatt.rxcontrols.RXMaterialDoubleField;
import io.github.leewyatt.rxcontrols.RXMaterialIntegerField;
import io.github.leewyatt.rxcontrols.RXMaterialLongField;
import io.github.leewyatt.rxcontrols.RXMaterialPasswordField;
import io.github.leewyatt.rxcontrols.RXMaterialTextField;
import io.github.leewyatt.rxcontrols.RXMenuButton;
import io.github.leewyatt.rxcontrols.RXMonthPicker;
import io.github.leewyatt.rxcontrols.RXMonthRangePicker;
import io.github.leewyatt.rxcontrols.RXMonthRangeView;
import io.github.leewyatt.rxcontrols.RXMonthView;
import io.github.leewyatt.rxcontrols.RXPasswordField;
import io.github.leewyatt.rxcontrols.RXPlaceholder;
import io.github.leewyatt.rxcontrols.RXQuarterPicker;
import io.github.leewyatt.rxcontrols.RXQuarterRangePicker;
import io.github.leewyatt.rxcontrols.RXQuarterRangeView;
import io.github.leewyatt.rxcontrols.RXQuarterView;
import io.github.leewyatt.rxcontrols.RXRadioButton;
import io.github.leewyatt.rxcontrols.RXRangeSlider;
import io.github.leewyatt.rxcontrols.RXReactionButton;
import io.github.leewyatt.rxcontrols.RXRibbonBadge;
import io.github.leewyatt.rxcontrols.RXRipplePane;
import io.github.leewyatt.rxcontrols.RXSeekBar;
import io.github.leewyatt.rxcontrols.RXSegmentedControl;
import io.github.leewyatt.rxcontrols.RXSegmentedProgressBar;
import io.github.leewyatt.rxcontrols.RXSegmentedStepIndicator;
import io.github.leewyatt.rxcontrols.RXSelectionBox;
import io.github.leewyatt.rxcontrols.RXSidebar;
import io.github.leewyatt.rxcontrols.RXSkeleton;
import io.github.leewyatt.rxcontrols.RXSlider;
import io.github.leewyatt.rxcontrols.RXSmoothScroller;
import io.github.leewyatt.rxcontrols.RXSnackbarHost;
import io.github.leewyatt.rxcontrols.RXSpeedDial;
import io.github.leewyatt.rxcontrols.RXStatePane;
import io.github.leewyatt.rxcontrols.RXSwitchButton;
import io.github.leewyatt.rxcontrols.RXTabPane;
import io.github.leewyatt.rxcontrols.RXTextField;
import io.github.leewyatt.rxcontrols.RXTextView;
import io.github.leewyatt.rxcontrols.RXTileView;
import io.github.leewyatt.rxcontrols.RXTimePicker;
import io.github.leewyatt.rxcontrols.RXTimeRangePicker;
import io.github.leewyatt.rxcontrols.RXTimeView;
import io.github.leewyatt.rxcontrols.RXTimelineView;
import io.github.leewyatt.rxcontrols.RXTitledPane;
import io.github.leewyatt.rxcontrols.RXToggleButton;
import io.github.leewyatt.rxcontrols.RXTransitionButton;
import io.github.leewyatt.rxcontrols.RXTransitionLabel;
import io.github.leewyatt.rxcontrols.RXTransitionPane;
import io.github.leewyatt.rxcontrols.RXWaveProgressIndicator;
import io.github.leewyatt.rxcontrols.RXWeekPicker;
import io.github.leewyatt.rxcontrols.RXWeekRangePicker;
import io.github.leewyatt.rxcontrols.RXWeekRangeView;
import io.github.leewyatt.rxcontrols.RXWeekView;
import io.github.leewyatt.rxcontrols.RXYearPicker;
import io.github.leewyatt.rxcontrols.RXYearRangePicker;
import io.github.leewyatt.rxcontrols.RXYearRangeView;
import io.github.leewyatt.rxcontrols.RXYearView;
import io.github.leewyatt.rxcontrols.layout.RXBox;
import io.github.leewyatt.rxcontrols.layout.RXFlowPane;
import io.github.leewyatt.rxcontrols.layout.RXMasonryPane;
import io.github.leewyatt.rxcontrols.layout.RXRow;
import io.github.leewyatt.rxcontrols.layout.RXTilePane;
import io.github.leewyatt.rxcontrols.samples.demo.RXAccordionDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXAutoCompleteFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXAutoCompletionDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXAvatarDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXBackdropDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXBadgeDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXBarSpinnerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXBoxDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXBreadcrumbBarDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXBurstsDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXCalendarViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXCascaderDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXCascaderViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXCheckBoxDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXChipInputDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXCircularProgressIndicatorDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXClipPathImageViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXCopyButtonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDatePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDateRangePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDateRangeViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDateTimePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDateTimeRangePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDateTimeViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDecimalFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDialogDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDigitDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDotPulseDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDoubleFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXDrawerPaneDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXFillButtonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXFlowPaneDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXHighlightTextViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXHoldButtonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXImagePaneDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXImageViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXIntegerFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXKanbanViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXLineButtonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXListViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXLongFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXLrcViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMasonryPaneDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMasonryViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMaterialDecimalFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMaterialDoubleFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMaterialIntegerFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMaterialLongFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMaterialPasswordFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMaterialTextFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMenuButtonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMonthPickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMonthRangePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMonthRangeViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXMonthViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXPasswordFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXQuarterPickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXQuarterRangePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXQuarterRangeViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXQuarterViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXRadioButtonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXRangeSliderDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXReactionButtonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXRowDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSeekBarDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSegmentedControlDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSegmentedProgressBarDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSegmentedStepIndicatorDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSkeletonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSliderDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSmoothScrollerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSnackbarHostDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSpeedDialDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXStatePaneDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXSwitchButtonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTabPaneDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTextFieldDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTextViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTilePaneDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTileViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTimePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTimeRangePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTimeViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTimelineViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTitledPaneDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXTransitionButtonDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXWaveProgressIndicatorDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXWeekPickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXWeekRangePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXWeekRangeViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXWeekViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXYearPickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXYearRangePickerDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXYearRangeViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.RXYearViewDemo;
import io.github.leewyatt.rxcontrols.samples.demo.carousel.SimpleDemo;
import io.github.leewyatt.rxcontrols.samples.showcase.RXAccordionShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXAudioSpectrumShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXAutoCompleteFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXAutoCompletionShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXAvatarShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBackdropShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBadgeShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBarSpinnerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBoxShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBreadcrumbBarShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXBurstsShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXCalendarViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXCarouselShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXCascaderLazyShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXCascaderShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXCascaderViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXCheckBoxShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXChipInputShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXChipSetShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXChipShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXCircularProgressIndicatorShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXClipPathImageViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXCopyButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDatePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDateRangePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDateRangeViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDateTimePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDateTimeRangePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDateTimeViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDecimalFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDialogShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDigitShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDotPulseShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDoubleFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDrawerPaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXDualPaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXFillButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXFillLabelShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXFlowPaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXHighlightTextViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXHoldButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXImagePaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXImageViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXIntegerFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXKanbanViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXLineButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXLineLabelShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXListViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXLongFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXLrcLineViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXLrcViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMasonryPaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMasonryViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialDecimalFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialDoubleFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialIntegerFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialLongFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialPasswordFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMaterialTextFieldShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMenuButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMonthPickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMonthRangePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMonthRangeViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXMonthViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXPlaceholderShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXQuarterPickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXQuarterRangePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXQuarterRangeViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXQuarterViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXRadioButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXRangeSliderShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXReactionButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXRibbonBadgeShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXRipplePaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXRowShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSeekBarShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSegmentedControlShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSegmentedProgressBarShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSegmentedStepIndicatorShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSelectionBoxShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSidebarShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSkeletonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSliderShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSmoothScrollerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSnackbarHostShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSpeedDialShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXStatePaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXSwitchButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTabPaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTextViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTilePaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTileViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTimePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTimeRangePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTimeViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTimelineViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTitledPaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXToggleButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTransitionButtonShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTransitionLabelShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXTransitionPaneShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXWaveProgressIndicatorShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXWeekPickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXWeekRangePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXWeekRangeViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXWeekViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXYearPickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXYearRangePickerShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXYearRangeViewShowcase;
import io.github.leewyatt.rxcontrols.samples.showcase.RXYearViewShowcase;
import io.github.leewyatt.rxcontrols.samples.support.RXShowcaseApplication;
import javafx.application.Application;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The sampler entries in navigation order. Every entry names its showcase and demo
 * classes directly, so a renamed or removed sample breaks the build instead of
 * silently dropping a page.
 */
final class SamplerCatalog {

    private static final List<SamplerEntry> ENTRIES = build();

    private SamplerCatalog() {
    }

    static List<SamplerEntry> entries() {
        return ENTRIES;
    }

    private static List<SamplerEntry> build() {
        List<SamplerEntry> list = new ArrayList<>();
        list.add(new SamplerEntry("Overview", SamplerCategory.START, List.of()));

        SamplerCategory c = SamplerCategory.BUTTONS;
        list.add(single(c, RXButton.class, RXButtonShowcase::new, null));
        list.add(single(c, RXFillButton.class, RXFillButtonShowcase::new, RXFillButtonDemo.class));
        list.add(single(c, RXLineButton.class, RXLineButtonShowcase::new, RXLineButtonDemo.class));
        list.add(single(c, RXHoldButton.class, RXHoldButtonShowcase::new, RXHoldButtonDemo.class));
        list.add(single(c, RXCopyButton.class, RXCopyButtonShowcase::new, RXCopyButtonDemo.class));
        list.add(single(c, RXReactionButton.class, RXReactionButtonShowcase::new, RXReactionButtonDemo.class));
        list.add(single(c, RXToggleButton.class, RXToggleButtonShowcase::new, null));
        list.add(single(c, RXTransitionButton.class, RXTransitionButtonShowcase::new, RXTransitionButtonDemo.class));
        list.add(single(c, RXMenuButton.class, RXMenuButtonShowcase::new, RXMenuButtonDemo.class));
        list.add(single(c, RXSpeedDial.class, RXSpeedDialShowcase::new, RXSpeedDialDemo.class));

        c = SamplerCategory.SELECTION;
        list.add(single(c, RXCheckBox.class, RXCheckBoxShowcase::new, RXCheckBoxDemo.class));
        list.add(single(c, RXRadioButton.class, RXRadioButtonShowcase::new, RXRadioButtonDemo.class));
        list.add(single(c, RXSwitchButton.class, RXSwitchButtonShowcase::new, RXSwitchButtonDemo.class));
        list.add(single(c, RXSegmentedControl.class, RXSegmentedControlShowcase::new, RXSegmentedControlDemo.class));
        list.add(single(c, RXSlider.class, RXSliderShowcase::new, RXSliderDemo.class));
        list.add(single(c, RXRangeSlider.class, RXRangeSliderShowcase::new, RXRangeSliderDemo.class));
        list.add(single(c, RXSeekBar.class, RXSeekBarShowcase::new, RXSeekBarDemo.class));
        list.add(single(c, RXChip.class, RXChipShowcase::new, null));
        list.add(single(c, RXChipSet.class, RXChipSetShowcase::new, null));
        list.add(single(c, RXSelectionBox.class, RXSelectionBoxShowcase::new, null));

        c = SamplerCategory.INPUT;
        list.add(single(c, RXTextField.class, null, RXTextFieldDemo.class));
        list.add(single(c, RXPasswordField.class, null, RXPasswordFieldDemo.class));
        list.add(family(c, "Number fields",
                variant("Integer", RXIntegerField.class, RXIntegerFieldShowcase::new, RXIntegerFieldDemo.class),
                variant("Long", RXLongField.class, RXLongFieldShowcase::new, RXLongFieldDemo.class),
                variant("Double", RXDoubleField.class, RXDoubleFieldShowcase::new, RXDoubleFieldDemo.class),
                variant("Decimal", RXDecimalField.class, RXDecimalFieldShowcase::new, RXDecimalFieldDemo.class)));
        list.add(single(c, RXMaterialTextField.class, RXMaterialTextFieldShowcase::new,
                RXMaterialTextFieldDemo.class));
        list.add(single(c, RXMaterialPasswordField.class, RXMaterialPasswordFieldShowcase::new,
                RXMaterialPasswordFieldDemo.class));
        list.add(family(c, "Material number fields",
                variant("Integer", RXMaterialIntegerField.class, RXMaterialIntegerFieldShowcase::new,
                        RXMaterialIntegerFieldDemo.class),
                variant("Long", RXMaterialLongField.class, RXMaterialLongFieldShowcase::new,
                        RXMaterialLongFieldDemo.class),
                variant("Double", RXMaterialDoubleField.class, RXMaterialDoubleFieldShowcase::new,
                        RXMaterialDoubleFieldDemo.class),
                variant("Decimal", RXMaterialDecimalField.class, RXMaterialDecimalFieldShowcase::new,
                        RXMaterialDecimalFieldDemo.class)));
        list.add(single(c, RXAutoCompleteField.class, RXAutoCompleteFieldShowcase::new,
                RXAutoCompleteFieldDemo.class));
        list.add(single(c, RXAutoCompletion.class, RXAutoCompletionShowcase::new, RXAutoCompletionDemo.class));
        list.add(single(c, RXChipInput.class, RXChipInputShowcase::new, RXChipInputDemo.class));

        c = SamplerCategory.DATE_TIME;
        list.add(family(c, "Date",
                variant("Picker", RXDatePicker.class, RXDatePickerShowcase::new, RXDatePickerDemo.class),
                variant("Range picker", RXDateRangePicker.class, RXDateRangePickerShowcase::new,
                        RXDateRangePickerDemo.class),
                variant("Calendar", RXCalendarView.class, RXCalendarViewShowcase::new, RXCalendarViewDemo.class),
                variant("Range view", RXDateRangeView.class, RXDateRangeViewShowcase::new,
                        RXDateRangeViewDemo.class)));
        list.add(family(c, "Date & time",
                variant("Picker", RXDateTimePicker.class, RXDateTimePickerShowcase::new, RXDateTimePickerDemo.class),
                variant("Range picker", RXDateTimeRangePicker.class, RXDateTimeRangePickerShowcase::new,
                        RXDateTimeRangePickerDemo.class),
                variant("View", RXDateTimeView.class, RXDateTimeViewShowcase::new, RXDateTimeViewDemo.class)));
        list.add(family(c, "Time",
                variant("Picker", RXTimePicker.class, RXTimePickerShowcase::new, RXTimePickerDemo.class),
                variant("Range picker", RXTimeRangePicker.class, RXTimeRangePickerShowcase::new,
                        RXTimeRangePickerDemo.class),
                variant("View", RXTimeView.class, RXTimeViewShowcase::new, RXTimeViewDemo.class)));
        list.add(family(c, "Week",
                variant("Picker", RXWeekPicker.class, RXWeekPickerShowcase::new, RXWeekPickerDemo.class),
                variant("Range picker", RXWeekRangePicker.class, RXWeekRangePickerShowcase::new,
                        RXWeekRangePickerDemo.class),
                variant("View", RXWeekView.class, RXWeekViewShowcase::new, RXWeekViewDemo.class),
                variant("Range view", RXWeekRangeView.class, RXWeekRangeViewShowcase::new,
                        RXWeekRangeViewDemo.class)));
        list.add(family(c, "Month",
                variant("Picker", RXMonthPicker.class, RXMonthPickerShowcase::new, RXMonthPickerDemo.class),
                variant("Range picker", RXMonthRangePicker.class, RXMonthRangePickerShowcase::new,
                        RXMonthRangePickerDemo.class),
                variant("View", RXMonthView.class, RXMonthViewShowcase::new, RXMonthViewDemo.class),
                variant("Range view", RXMonthRangeView.class, RXMonthRangeViewShowcase::new,
                        RXMonthRangeViewDemo.class)));
        list.add(family(c, "Quarter",
                variant("Picker", RXQuarterPicker.class, RXQuarterPickerShowcase::new, RXQuarterPickerDemo.class),
                variant("Range picker", RXQuarterRangePicker.class, RXQuarterRangePickerShowcase::new,
                        RXQuarterRangePickerDemo.class),
                variant("View", RXQuarterView.class, RXQuarterViewShowcase::new, RXQuarterViewDemo.class),
                variant("Range view", RXQuarterRangeView.class, RXQuarterRangeViewShowcase::new,
                        RXQuarterRangeViewDemo.class)));
        list.add(family(c, "Year",
                variant("Picker", RXYearPicker.class, RXYearPickerShowcase::new, RXYearPickerDemo.class),
                variant("Range picker", RXYearRangePicker.class, RXYearRangePickerShowcase::new,
                        RXYearRangePickerDemo.class),
                variant("View", RXYearView.class, RXYearViewShowcase::new, RXYearViewDemo.class),
                variant("Range view", RXYearRangeView.class, RXYearRangeViewShowcase::new,
                        RXYearRangeViewDemo.class)));

        c = SamplerCategory.DATA_VIEWS;
        list.add(single(c, RXListView.class, RXListViewShowcase::new, RXListViewDemo.class));
        list.add(single(c, RXTileView.class, RXTileViewShowcase::new, RXTileViewDemo.class));
        list.add(single(c, RXMasonryView.class, RXMasonryViewShowcase::new, RXMasonryViewDemo.class));
        list.add(single(c, RXKanbanView.class, RXKanbanViewShowcase::new, RXKanbanViewDemo.class));
        list.add(single(c, RXTimelineView.class, RXTimelineViewShowcase::new, RXTimelineViewDemo.class));
        list.add(family(c, "RXCascader",
                variant("Options", RXCascader.class, RXCascaderShowcase::new, RXCascaderDemo.class),
                variant("Lazy loading", RXCascader.class, RXCascaderLazyShowcase::new, RXCascaderDemo.class)));
        list.add(single(c, RXCascaderView.class, RXCascaderViewShowcase::new, RXCascaderViewDemo.class));
        list.add(single(c, RXTextView.class, RXTextViewShowcase::new, RXTextViewDemo.class));
        list.add(single(c, RXHighlightTextView.class, RXHighlightTextViewShowcase::new,
                RXHighlightTextViewDemo.class));

        c = SamplerCategory.FEEDBACK;
        list.add(single(c, RXBadge.class, RXBadgeShowcase::new, RXBadgeDemo.class));
        list.add(single(c, RXRibbonBadge.class, RXRibbonBadgeShowcase::new, null));
        list.add(single(c, RXCircularProgressIndicator.class, RXCircularProgressIndicatorShowcase::new,
                RXCircularProgressIndicatorDemo.class));
        list.add(single(c, RXWaveProgressIndicator.class, RXWaveProgressIndicatorShowcase::new,
                RXWaveProgressIndicatorDemo.class));
        list.add(single(c, RXSegmentedProgressBar.class, RXSegmentedProgressBarShowcase::new,
                RXSegmentedProgressBarDemo.class));
        list.add(single(c, RXSegmentedStepIndicator.class, RXSegmentedStepIndicatorShowcase::new,
                RXSegmentedStepIndicatorDemo.class));
        list.add(single(c, RXBarSpinner.class, RXBarSpinnerShowcase::new, RXBarSpinnerDemo.class));
        list.add(single(c, RXDotPulse.class, RXDotPulseShowcase::new, RXDotPulseDemo.class));
        list.add(single(c, RXSkeleton.class, RXSkeletonShowcase::new, RXSkeletonDemo.class));
        list.add(single(c, RXPlaceholder.class, RXPlaceholderShowcase::new, null));
        list.add(single(c, RXStatePane.class, RXStatePaneShowcase::new, RXStatePaneDemo.class));
        list.add(single(c, RXSnackbarHost.class, RXSnackbarHostShowcase::new, RXSnackbarHostDemo.class));
        list.add(single(c, RXDialog.class, RXDialogShowcase::new, RXDialogDemo.class));
        list.add(single(c, RXBackdrop.class, RXBackdropShowcase::new, RXBackdropDemo.class));

        c = SamplerCategory.NAVIGATION;
        list.add(single(c, RXSidebar.class, RXSidebarShowcase::new, null));
        list.add(single(c, RXTabPane.class, RXTabPaneShowcase::new, RXTabPaneDemo.class));
        list.add(single(c, RXBreadcrumbBar.class, RXBreadcrumbBarShowcase::new, RXBreadcrumbBarDemo.class));
        list.add(single(c, RXAccordion.class, RXAccordionShowcase::new, RXAccordionDemo.class));
        list.add(single(c, RXTitledPane.class, RXTitledPaneShowcase::new, RXTitledPaneDemo.class));
        list.add(single(c, RXDrawerPane.class, RXDrawerPaneShowcase::new, RXDrawerPaneDemo.class));
        list.add(single(c, RXDualPane.class, RXDualPaneShowcase::new, null));
        list.add(single(c, RXCarousel.class, RXCarouselShowcase::new, SimpleDemo.class));
        list.add(single(c, RXTransitionPane.class, RXTransitionPaneShowcase::new, null));

        c = SamplerCategory.MEDIA;
        list.add(single(c, RXAvatar.class, RXAvatarShowcase::new, RXAvatarDemo.class));
        list.add(single(c, RXImageView.class, RXImageViewShowcase::new, RXImageViewDemo.class));
        list.add(single(c, RXImagePane.class, RXImagePaneShowcase::new, RXImagePaneDemo.class));
        list.add(single(c, RXClipPathImageView.class, RXClipPathImageViewShowcase::new,
                RXClipPathImageViewDemo.class));
        list.add(single(c, RXAudioSpectrum.class, RXAudioSpectrumShowcase::new, null));
        list.add(single(c, RXLrcView.class, RXLrcViewShowcase::new, RXLrcViewDemo.class));
        list.add(single(c, RXLrcLineView.class, RXLrcLineViewShowcase::new, null));
        list.add(single(c, RXDigit.class, RXDigitShowcase::new, RXDigitDemo.class));
        list.add(single(c, RXTransitionLabel.class, RXTransitionLabelShowcase::new, null));
        list.add(single(c, RXFillLabel.class, RXFillLabelShowcase::new, null));
        list.add(single(c, RXLineLabel.class, RXLineLabelShowcase::new, null));

        c = SamplerCategory.LAYOUT;
        list.add(single(c, RXBox.class, RXBoxShowcase::new, RXBoxDemo.class));
        list.add(family(c, "RXRow / RXCol", variant("RXRow", RXRow.class, RXRowShowcase::new, RXRowDemo.class)));
        list.add(single(c, RXFlowPane.class, RXFlowPaneShowcase::new, RXFlowPaneDemo.class));
        list.add(single(c, RXTilePane.class, RXTilePaneShowcase::new, RXTilePaneDemo.class));
        list.add(single(c, RXMasonryPane.class, RXMasonryPaneShowcase::new, RXMasonryPaneDemo.class));

        c = SamplerCategory.MOTION;
        list.add(single(c, RXBursts.class, RXBurstsShowcase::new, RXBurstsDemo.class));
        list.add(single(c, RXRipplePane.class, RXRipplePaneShowcase::new, null));
        list.add(single(c, RXSmoothScroller.class, RXSmoothScrollerShowcase::new, RXSmoothScrollerDemo.class));
        return List.copyOf(list);
    }

    private static SamplerEntry single(SamplerCategory category, Class<?> type,
                                       Supplier<RXShowcaseApplication> showcase,
                                       Class<? extends Application> demo) {
        return family(category, type.getSimpleName(), variant(type.getSimpleName(), type, showcase, demo));
    }

    private static SamplerEntry family(SamplerCategory category, String name, SamplerVariant... variants) {
        return new SamplerEntry(name, category, List.of(variants));
    }

    private static SamplerVariant variant(String label, Class<?> type, Supplier<RXShowcaseApplication> showcase,
                                          Class<? extends Application> demo) {
        return new SamplerVariant(label, type, showcase, demo);
    }
}
