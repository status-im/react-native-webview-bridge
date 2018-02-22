/**
 * Copyright (c) 2017-present, Status Research and Development GmbH.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 *
 */

#include "rctwkwebviewmanager.h"

#include <QDebug>
#include <QQmlComponent>
#include <QQmlProperty>
#include <QQuickItem>
#include <QString>
#include <QVariant>

#include "attachedproperties.h"
#include "bridge.h"
#include "layout/flexbox.h"
#include "propertyhandler.h"
#include "utilities.h"

using namespace utilities;

namespace {
struct RegisterQMLMetaType {
    RegisterQMLMetaType() {
        qRegisterMetaType<RCTWKWebViewManager*>();
    }
} registerMetaType;
} // namespace

class RCTWKWebViewManagerPrivate {};

RCTWKWebViewManager::RCTWKWebViewManager(QObject* parent) : ViewManager(parent), d_ptr(new RCTWKWebViewManagerPrivate) {}

RCTWKWebViewManager::~RCTWKWebViewManager() {}

QString RCTWKWebViewManager::moduleName() {
    return "RCTWKWebViewManager";
}

ViewManager* RCTWKWebViewManager::viewManager() {
    return this;
}

void RCTWKWebViewManager::configureView(QQuickItem* view) const {
    ViewManager::configureView(view);
}

#include "rctwkwebviewmanager.moc"

