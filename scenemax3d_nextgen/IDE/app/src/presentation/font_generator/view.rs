use super::*;
fn node(c: &mut Commands, parent: Entity, n: Node) -> Entity {
    c.spawn((n, ChildOf(parent))).id()
}
fn label(c: &mut Commands, parent: Entity, text: impl Into<String>, size: f32) -> Entity {
    c.spawn((scenemax_ide_ui::label(text, size), ChildOf(parent)))
        .id()
}
fn button(c: &mut Commands, parent: Entity, title: &str, action: Action) -> Entity {
    let e = scenemax_ide_ui::button(c, parent, title, Name::new(title.to_owned()));
    c.entity(e)
        .observe(move |mut e: On<Pointer<Click>>, mut s: ResMut<State>| {
            if e.button == PointerButton::Primary {
                e.propagate(false);
                s.actions.push(action.clone());
            }
        });
    e
}
fn row(c: &mut Commands, parent: Entity) -> Entity {
    node(
        c,
        parent,
        Node {
            align_items: AlignItems::Center,
            column_gap: px(6.),
            flex_shrink: 0.,
            ..default()
        },
    )
}
fn field_row(c: &mut Commands, parent: Entity, title: &str) -> Entity {
    let r = row(c, parent);
    let l = label(c, r, title, 12.);
    c.entity(l).insert(Node {
        width: px(115.),
        flex_shrink: 0.,
        ..default()
    });
    node(
        c,
        r,
        Node {
            flex_grow: 1.,
            min_width: px(0.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    )
}
fn input(c: &mut Commands, parent: Entity, title: &str, f: Field, value: &str) {
    let r = field_row(c, parent, title);
    property::input(c, r, f, value);
}
fn choice(c: &mut Commands, parent: Entity, title: &str, f: Field, value: &str, values: &[String]) {
    let r = field_row(c, parent, title);
    property::dropdown(c, r, f, value, values);
}
fn numeric(c: &mut Commands, parent: Entity, title: &str, f: Field, value: i32) {
    let p = field_row(c, parent, title);
    let r = row(c, p);
    let holder = node(
        c,
        r,
        Node {
            flex_grow: 1.,
            min_width: px(20.),
            ..default()
        },
    );
    property::input(c, holder, f, &value.to_string());
    button(c, r, "−", Action::Step(f, -1));
    button(c, r, "+", Action::Step(f, 1));
}
fn modal(c: &mut Commands, parent: Option<Entity>, z: i32) -> (Entity, Entity) {
    let host = c
        .spawn((
            Node {
                position_type: PositionType::Absolute,
                width: percent(100.),
                height: percent(100.),
                align_items: AlignItems::Center,
                justify_content: JustifyContent::Center,
                ..default()
            },
            BackgroundColor(Color::srgba(0., 0., 0., 0.55)),
            GlobalZIndex(z),
            TabGroup::modal(),
        ))
        .id();
    if let Some(parent) = parent {
        c.entity(host).insert(ChildOf(parent));
    }
    let panel = node(
        c,
        host,
        Node {
            width: px(620.),
            max_width: percent(95.),
            padding: px(16.).all(),
            row_gap: px(10.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    c.entity(panel).insert(BackgroundColor(PANEL));
    (host, panel)
}
pub(super) fn shell(c: &mut Commands, s: &mut State) {
    let (host, panel) = modal(c, None, 360);
    s.host = Some(host);
    c.entity(panel).insert(Node {
        width: percent(94.),
        height: percent(94.),
        padding: px(14.).all(),
        row_gap: px(10.),
        flex_direction: FlexDirection::Column,
        ..default()
    });
    label(c, panel, "Font Generator", 20.);
    if s.catalog.is_none() {
        label(c, panel, "Loading installed system fonts…", 14.);
        button(c, panel, "Close", Action::Close);
        return;
    }
    let body = node(
        c,
        panel,
        Node {
            flex_grow: 1.,
            min_height: px(0.),
            column_gap: px(16.),
            ..default()
        },
    );
    let left = node(
        c,
        body,
        Node {
            width: percent(36.),
            min_width: px(370.),
            max_width: px(520.),
            flex_shrink: 0.,
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    let form = property::scroll_column(c, left);
    let o = &s.options;
    input(c, form, "Asset Name", Field::Name, &o.name);
    choice(
        c,
        form,
        "System Font",
        Field::Family,
        &o.family,
        &s.catalog.as_ref().map(Catalog::names).unwrap_or_default(),
    );
    let styles = ["Plain", "Bold", "Italic", "Bold Italic"].map(str::to_owned);
    choice(
        c,
        form,
        "Style",
        Field::Style,
        &styles[o.style as usize],
        &styles,
    );
    for (title, field) in [
        ("Font Size", Field::Size),
        ("Outline Width", Field::Outline),
        ("Shadow X", Field::ShadowX),
        ("Shadow Y", Field::ShadowY),
        ("Padding", Field::Padding),
    ] {
        numeric(c, form, title, field, number(o, field));
    }
    for (index, title) in [
        "Fill Color",
        "Gradient Color",
        "Outline Color",
        "Shadow Color",
    ]
    .iter()
    .enumerate()
    {
        let p = field_row(c, form, title);
        let r = row(c, p);
        let swatch = button(c, r, "   ", Action::Color(index));
        let v = o.colors[index];
        c.entity(swatch).insert((
            Swatch(index),
            BackgroundColor(Color::srgba_u8(v[0], v[1], v[2], v[3])),
            Node {
                width: px(40.),
                height: px(26.),
                border: px(1.).all(),
                flex_shrink: 0.,
                ..default()
            },
            BorderColor::all(EDGE),
        ));
        let holder = node(
            c,
            r,
            Node {
                flex_grow: 1.,
                min_width: px(60.),
                ..default()
            },
        );
        property::input(c, holder, Field::Color(index), &hex(v));
    }
    input(c, form, "Preview Text", Field::Preview, &o.preview);
    choice(
        c,
        form,
        "Character Set",
        Field::Charset,
        &s.charset,
        &["Latin + Hebrew", "Basic Latin", "Custom"].map(str::to_owned),
    );
    let r = field_row(c, form, "Characters");
    let e = scenemax_ide_ui::spawn_editor(c, r, Field::Characters, &o.characters, false);
    c.entity(e).insert((
        Node {
            width: percent(100.),
            height: px(96.),
            padding: px(6.).all(),
            overflow: Overflow::scroll_y(),
            ..default()
        },
        EditableText {
            max_characters: Some(4096),
            ..EditableText::new(&o.characters)
        },
        TextLayout::linebreak(bevy::text::LineBreak::AnyCharacter),
        TextFont {
            font_size: FontSize::Px(13.),
            ..default()
        },
        BackgroundColor(BG),
    ));
    let toggles = row(c, form);
    property::checkbox(c, toggles, Field::Smooth, "Smooth edges", o.smooth);
    property::checkbox(c, toggles, Field::Gradient, "Gradient fill", o.gradient);
    let right = node(
        c,
        body,
        Node {
            flex_grow: 1.,
            min_width: px(0.),
            row_gap: px(10.),
            flex_direction: FlexDirection::Column,
            ..default()
        },
    );
    label(c, right, "Preview", 14.);
    let preview_box = node(
        c,
        right,
        Node {
            flex_grow: 1.,
            min_height: px(150.),
            align_items: AlignItems::Center,
            justify_content: JustifyContent::Center,
            padding: px(12.).all(),
            overflow: Overflow::clip(),
            ..default()
        },
    );
    c.entity(preview_box).insert(BackgroundColor(BG));
    let preview = node(
        c,
        preview_box,
        Node {
            width: percent(100.),
            max_width: px(640.),
            aspect_ratio: Some(2.),
            ..default()
        },
    );
    c.entity(preview).insert(Preview);
    if let Some(image) = &s.image {
        c.entity(preview).insert(ImageNode::new(image.clone()));
    }
    label(
        c,
        right,
        "Exports an AngelCode bitmap font pair (.fnt + .png) into the active project's resources/fonts folder.\nThe exported font is also registered in fonts-ext.json so SceneMax can load it at runtime.",
        13.,
    );
    let status = label(c, panel, &s.status, 12.);
    c.entity(status).insert(Status);
    let footer = node(
        c,
        panel,
        Node {
            justify_content: JustifyContent::FlexEnd,
            column_gap: px(8.),
            flex_shrink: 0.,
            ..default()
        },
    );
    button(c, footer, "Refresh Preview", Action::Refresh);
    button(c, footer, "Export To Project", Action::Export);
    button(c, footer, "Close", Action::Close);
}
pub(super) fn picker(c: &mut Commands, s: &mut State) {
    if let Some(e) = s.picker.take() {
        c.entity(e).try_despawn();
    }
    let (host, panel) = modal(c, s.host, 470);
    s.picker = Some(host);
    let index = s.color.unwrap_or(0);
    label(
        c,
        panel,
        [
            "Fill Color",
            "Gradient Color",
            "Outline Color",
            "Shadow Color",
        ][index],
        19.,
    );
    let swatch = node(
        c,
        panel,
        Node {
            height: px(60.),
            ..default()
        },
    );
    c.entity(swatch)
        .insert((PickerSwatch, BackgroundColor(Color::WHITE)));
    for (i, title) in ["Red", "Green", "Blue", "Alpha"].iter().enumerate() {
        let p = field_row(c, panel, title);
        scenemax_ide_ui::number::input(
            c,
            p,
            Field::Channel(i),
            &s.draft_color[i].to_string(),
            0.,
            255.,
            1.,
        );
    }
    label(
        c,
        panel,
        "Drag the channels or enter exact 0–255 values. Hex values can also be entered in the main form.",
        12.,
    );
    let r = row(c, panel);
    button(c, r, "Cancel", Action::CancelColor);
    button(c, r, "Use Color", Action::AcceptColor);
}
pub(super) fn notice(c: &mut Commands, s: &mut State, title: &str, message: &str) {
    if let Some(e) = s.notice.take() {
        c.entity(e).try_despawn();
    }
    let (host, panel) = modal(c, s.host, 480);
    s.notice = Some(host);
    label(c, panel, title, 20.);
    label(c, panel, message, 13.);
    button(c, panel, "OK", Action::Dismiss);
}
